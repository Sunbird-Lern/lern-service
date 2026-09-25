package org.sunbird.viewer.actor

import org.scalamock.scalatest.MockFactory
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.sunbird.cassandra.CassandraOperation
import org.sunbird.request.RequestContext
import org.sunbird.response.Response
import org.sunbird.viewer.actor.CarryForwardRules.Target

import java.util

// Copy overlay: which prior completions carry into a target (course, batch), per scope + rules.
class CarryForwardServiceTest extends AnyFlatSpec with Matchers with MockFactory {

  private val ctx: RequestContext = null
  private val now = 1_000_000_000_000L
  private val always = """{"match":"all","rules":[{"type":"always"}]}"""
  private val noTarget = Target(None, None, None)

  private def uccRow(coll: String, cxt: String, cont: String, status: Int): util.Map[String, AnyRef] = new util.HashMap[String, AnyRef]() {{
    put("collectionid", coll); put("contextid", cxt); put("contentid", cont); put("status", Integer.valueOf(status))
    put("last_completed_time", Long.box(now - 86400000L))
  }}
  private def resp(rows: util.Map[String, AnyRef]*): Response = {
    val r = new Response; val l = new util.ArrayList[util.Map[String, AnyRef]](); rows.foreach(l.add); r.put("response", l); r
  }

  private def stubReads(ops: CassandraOperation, sourceRows: Response): Unit = {
    (ops.getRecords(_: String, _: String, _: util.Map[String, AnyRef], _: util.List[String], _: RequestContext))
      .expects(where { (_: String, _: String, f: util.Map[String, AnyRef], _: util.List[String], _: RequestContext) => f.containsKey("userid") })
      .returning(sourceRows).anyNumberOfTimes()
    (ops.getRecords(_: String, _: String, _: util.Map[String, AnyRef], _: util.List[String], _: RequestContext))
      .expects(where { (_: String, _: String, f: util.Map[String, AnyRef], _: util.List[String], _: RequestContext) => f.containsKey("user_id") })
      .returning(resp()).anyNumberOfTimes()
  }
  private def expectCarry(ops: CassandraOperation): Unit =
    (ops.upsertRecord(_: String, _: String, _: util.Map[String, AnyRef], _: RequestContext))
      .expects(where { (_: String, _: String, row: util.Map[String, AnyRef], _: RequestContext) =>
        row.get("collectionid") == "c1" && row.get("contextid") == "b1" && row.get("contentid") == "leaf1" && row.get("status") == Integer.valueOf(2) })
      .returning(new Response()).once()

  "content scope" should "carry an organic completion" in {
    val ops = mock[CassandraOperation]; stubReads(ops, resp(uccRow("leaf1", "leaf1", "leaf1", 2))); expectCarry(ops)
    new CarryForwardService(ops).carryForward("u1", "c1", "b1", List("leaf1"), "content", noTarget, now, ctx, always) shouldBe 1
  }

  "collection scope" should "NOT carry an organic completion" in {
    val ops = mock[CassandraOperation]; stubReads(ops, resp(uccRow("leaf1", "leaf1", "leaf1", 2))) // organic
    new CarryForwardService(ops).carryForward("u1", "c1", "b1", List("leaf1"), "collection", noTarget, now, ctx, always) shouldBe 0
  }

  "collection scope" should "carry an in-collection completion" in {
    val ops = mock[CassandraOperation]; stubReads(ops, resp(uccRow("c2", "b2", "leaf1", 2))); expectCarry(ops)
    new CarryForwardService(ops).carryForward("u1", "c1", "b1", List("leaf1"), "collection", noTarget, now, ctx, always) shouldBe 1
  }

  // Production CassandraUtil renames some columns on read (cassandratablecolumn.properties):
  // contentid -> contentId, last_completed_time -> lastCompletedTime; collectionid/contextid/status keep their names.
  private def prodUccRow(coll: String, cxt: String, cont: String, status: Int, completedAt: Long): util.Map[String, AnyRef] =
    new util.HashMap[String, AnyRef]() {{
      put("collectionid", coll); put("contextid", cxt); put("contentId", cont); put("status", Integer.valueOf(status))
      put("lastCompletedTime", new java.util.Date(completedAt))
    }}

  "content scope" should "carry a production-cased row (contentId / lastCompletedTime)" in {
    val ops = mock[CassandraOperation]; stubReads(ops, resp(prodUccRow("leaf1", "leaf1", "leaf1", 2, now - 86400000L))); expectCarry(ops)
    new CarryForwardService(ops).carryForward("u1", "c1", "b1", List("leaf1"), "content", noTarget, now, ctx, always) shouldBe 1
  }

  "completedWithin" should "read the completion time from a production-cased row" in {
    val ops = mock[CassandraOperation]; stubReads(ops, resp(prodUccRow("leaf1", "leaf1", "leaf1", 2, now - 86400000L))); expectCarry(ops)
    val within3Days = """{"match":"all","rules":[{"type":"completedWithin","unit":"days","value":3}]}"""
    new CarryForwardService(ops).carryForward("u1", "c1", "b1", List("leaf1"), "content", noTarget, now, ctx, within3Days) shouldBe 1
  }

  "score carry" should "write the re-keyed assessment row back with real column names" in {
    val ops = mock[CassandraOperation]
    (ops.getRecords(_: String, _: String, _: util.Map[String, AnyRef], _: util.List[String], _: RequestContext))
      .expects(where { (_: String, _: String, f: util.Map[String, AnyRef], _: util.List[String], _: RequestContext) => f.containsKey("userid") })
      .returning(resp(prodUccRow("c2", "b2", "leaf1", 2, now - 86400000L))).anyNumberOfTimes()
    // assessment row as production returns it: attempt_id/last_attempted_on/total_score/total_max_score are camelCased
    (ops.getRecords(_: String, _: String, _: util.Map[String, AnyRef], _: util.List[String], _: RequestContext))
      .expects(where { (_: String, _: String, f: util.Map[String, AnyRef], _: util.List[String], _: RequestContext) => f.containsKey("user_id") })
      .returning(resp(new util.HashMap[String, AnyRef]() {{
        put("collection_id", "c2"); put("context_id", "b2"); put("user_id", "u1"); put("content_id", "leaf1")
        put("attemptId", "a1"); put("lastAttemptedOn", new java.util.Date(now)); put("totalScore", Double.box(4.0)); put("totalMaxScore", Double.box(5.0))
      }})).anyNumberOfTimes()
    (ops.upsertRecord(_: String, _: String, _: util.Map[String, AnyRef], _: RequestContext))
      .expects(where { (_: String, _: String, row: util.Map[String, AnyRef], _: RequestContext) => row.containsKey("contentid") })
      .returning(new Response()).once()
    (ops.upsertRecord(_: String, _: String, _: util.Map[String, AnyRef], _: RequestContext))
      .expects(where { (_: String, _: String, row: util.Map[String, AnyRef], _: RequestContext) =>
        row.get("collection_id") == "c1" && row.get("context_id") == "b1" && row.get("attempt_id") == "a1" &&
        row.get("total_score") == Double.box(4.0) && row.get("total_max_score") == Double.box(5.0) && row.containsKey("last_attempted_on") &&
        !row.containsKey("attemptId") && !row.containsKey("totalScore") })
      .returning(new Response()).once()
    new CarryForwardService(ops).carryForward("u1", "c1", "b1", List("leaf1"), "collection", noTarget, now, ctx, always) shouldBe 1
  }

  "a failing rule" should "carry nothing" in {
    val ops = mock[CassandraOperation]; stubReads(ops, resp(uccRow("leaf1", "leaf1", "leaf1", 2)))
    val tooOld = """{"match":"all","rules":[{"type":"completedWithin","unit":"days","value":0}]}"""
    new CarryForwardService(ops).carryForward("u1", "c1", "b1", List("leaf1"), "content", noTarget, now, ctx, tooOld) shouldBe 0
  }
}
