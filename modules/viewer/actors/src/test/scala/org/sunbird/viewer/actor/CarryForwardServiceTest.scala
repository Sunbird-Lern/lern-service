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

  "a failing rule" should "carry nothing" in {
    val ops = mock[CassandraOperation]; stubReads(ops, resp(uccRow("leaf1", "leaf1", "leaf1", 2)))
    val tooOld = """{"match":"all","rules":[{"type":"completedWithin","unit":"days","value":0}]}"""
    new CarryForwardService(ops).carryForward("u1", "c1", "b1", List("leaf1"), "content", noTarget, now, ctx, tooOld) shouldBe 0
  }
}
