package org.sunbird.viewer.actor

import java.util
import java.util.concurrent.TimeUnit

import org.apache.pekko.actor.{ActorSystem, Props}
import org.apache.pekko.testkit.TestKit
import org.scalamock.scalatest.MockFactory
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.sunbird.activity.util.HierarchyRelationsUtil
import org.sunbird.cassandra.CassandraOperation
import org.sunbird.common.PropertiesCache
import org.sunbird.request.{Request, RequestContext}
import org.sunbird.response.Response

import scala.concurrent.duration.FiniteDuration

/**
 * Unit tests for ViewerAggregatorActor guard branches (deterministic, no hierarchy fixture needed):
 *  - missing userId/courseId -> skip, still replies success.
 *  - no consumption rows -> early return, NO aggregate/enrolment writes.
 * The full recursive-rollup happy path depends on a published hierarchy_relations fixture and is
 * better exercised as an integration test; these guards pin the cheap-exit correctness.
 */
class ViewerAggregatorActorTest extends AnyFlatSpec with Matchers with MockFactory {

  val system: ActorSystem = ActorSystem.create("viewer-aggregator-test")

  private def emptyRows: Response = {
    val r = new Response(); r.put("response", new util.ArrayList[util.Map[String, AnyRef]]()); r
  }

  private def callActor(request: Request, props: Props): Response = {
    val probe = new TestKit(system)
    val actorRef = system.actorOf(props)
    actorRef.tell(request, probe.testActor)
    probe.expectMsgType[Response](FiniteDuration.apply(15, TimeUnit.SECONDS))
  }

  private def aggRequest(userId: String, courseId: String): Request = {
    val req = new Request
    req.setOperation("aggregate")
    if (userId != null) req.put("userId", userId)
    if (courseId != null) req.put("courseId", courseId)
    req.put("batchId", "b1")
    req
  }

  "aggregate" should "skip and reply success when userId/courseId are missing" in {
    val ops = mock[CassandraOperation]
    val hru = mock[HierarchyRelationsUtil]
    // no cassandra / hierarchy interaction expected on the missing-id guard
    val result = callActor(aggRequest(null, null), Props(new ViewerAggregatorActor().configure(ops, hru)))
    result should not be null
  }

  "aggregate" should "early-return with no writes when there is no consumption" in {
    val ops = mock[CassandraOperation]
    val hru = mock[HierarchyRelationsUtil]
    // readConsumption -> empty; must NOT reach batchUpdateWithPutAll / updateRecordV2
    (ops.getRecords(_: String, _: String, _: util.Map[String, AnyRef], _: util.List[String], _: RequestContext))
      .expects(*, *, *, *, *).returns(emptyRows)
    val result = callActor(aggRequest("u1", "c1"), Props(new ViewerAggregatorActor().configure(ops, hru)))
    result should not be null
  }

  // C2: contentstatus is a full-column replace in updateRecordV2, so the rollup must MERGE freshly computed
  // leaf statuses into the enrolment row's existing map — a root-keyed read that sees only some leaves must
  // not wipe the others. Tests the extracted pure merge directly (the full rollup is an integration concern).
  "mergeContentStatus" should "preserve existing leaves and add/overwrite the fresh ones" in {
    val existing = new util.HashMap[String, AnyRef]() {{
      put("leaf-a", Integer.valueOf(2)) // completed earlier, not in this pass
      put("leaf-b", Integer.valueOf(1)) // in-progress, gets overwritten below
    }}
    val fresh = Map[String, AnyRef]("leaf-b" -> Integer.valueOf(2), "leaf-c" -> Integer.valueOf(2))
    val merged = ViewerAggregatorActor.mergeContentStatus(existing, fresh)
    merged.get("leaf-a") shouldBe Integer.valueOf(2) // preserved (not clobbered)
    merged.get("leaf-b") shouldBe Integer.valueOf(2) // fresh wins on conflict
    merged.get("leaf-c") shouldBe Integer.valueOf(2) // added
    merged.size() shouldBe 3
  }

  "mergeContentStatus" should "tolerate a null existing map" in {
    val merged = ViewerAggregatorActor.mergeContentStatus(null, Map[String, AnyRef]("leaf-a" -> Integer.valueOf(1)))
    merged.get("leaf-a") shouldBe Integer.valueOf(1)
    merged.size() shouldBe 1
  }

  // --- Phase 2: NoContext rollup fans out from the collapsed trigger key to the user's real enrolments ---

  private def withMode[T](mode: String, scope: String)(body: => T): T = {
    val cache = PropertiesCache.getInstance()
    cache.saveConfigProperty("viewer_context_mode", mode)
    cache.saveConfigProperty("viewer_carry_scope", scope)
    try body finally {
      cache.saveConfigProperty("viewer_context_mode", "strict")
      cache.saveConfigProperty("viewer_carry_scope", "content")
    }
  }

  private def rowsOf(rows: util.Map[String, AnyRef]*): Response = {
    val r = new Response(); val l = new util.ArrayList[util.Map[String, AnyRef]](); rows.foreach(l.add); r.put("response", l); r
  }
  private def enrol(course: String, batch: String, status: Int): util.Map[String, AnyRef] = new util.HashMap[String, AnyRef]() {{
    put("courseId", course); put("batchId", batch); put("active", java.lang.Boolean.TRUE); put("status", Integer.valueOf(status))
  }}
  private def uccLeaf(leaf: String, status: Int): util.Map[String, AnyRef] = new util.HashMap[String, AnyRef]() {{
    put("contentid", leaf); put("status", Integer.valueOf(status))
  }}

  "aggregate in noContext content mode" should "fan out to the enrolment containing the leaf and update progress WITHOUT storing contentstatus" in {
    withMode("noContext", "content") {
      val ops = mock[CassandraOperation]
      val hru = mock[HierarchyRelationsUtil]
      // course c1 has leaves leaf1, leaf2; leaf1 sits directly under c1
      (hru.getLeafNodes(_: String, _: String, _: RequestContext)).expects("c1", "c1", *).returning(List("leaf1", "leaf2")).anyNumberOfTimes()
      (hru.getAncestors(_: String, _: String, _: RequestContext)).expects("c1", "leaf1", *).returning(List("c1")).anyNumberOfTimes()
      (hru.getOptionalNodes(_: String, _: String, _: RequestContext)).expects("c1", "c1", *).returning(List()).anyNumberOfTimes()
      // enrolments: {userid} only -> one active top-level enrolment (c1, b1); {userid,courseid,batchid} -> optional_nodes read
      (ops.getRecords(_: String, _: String, _: util.Map[String, AnyRef], _: util.List[String], _: RequestContext))
        .expects(where { (_: String, _: String, f: util.Map[String, AnyRef], _: util.List[String], _: RequestContext) =>
          f.containsKey("userid") && !f.containsKey("courseid") && !f.containsKey("collectionid") })
        .returning(rowsOf(enrol("c1", "b1", 1))).anyNumberOfTimes()
      (ops.getRecords(_: String, _: String, _: util.Map[String, AnyRef], _: util.List[String], _: RequestContext))
        .expects(where { (_: String, _: String, f: util.Map[String, AnyRef], _: util.List[String], _: RequestContext) => f.containsKey("courseid") })
        .returning(rowsOf(enrol("c1", "b1", 1))).anyNumberOfTimes()
      // leaf reads at (leaf,leaf,leaf): leaf1 done, leaf2 absent
      (ops.getRecords(_: String, _: String, _: util.Map[String, AnyRef], _: util.List[String], _: RequestContext))
        .expects(where { (_: String, _: String, f: util.Map[String, AnyRef], _: util.List[String], _: RequestContext) => f.get("contentid") == "leaf1" })
        .returning(rowsOf(uccLeaf("leaf1", 2))).anyNumberOfTimes()
      (ops.getRecords(_: String, _: String, _: util.Map[String, AnyRef], _: util.List[String], _: RequestContext))
        .expects(where { (_: String, _: String, f: util.Map[String, AnyRef], _: util.List[String], _: RequestContext) => f.get("contentid") == "leaf2" })
        .returning(emptyRows).anyNumberOfTimes()
      (ops.batchUpdateWithPutAll(_: String, _: String, _: util.List[util.Map[String, util.Map[String, AnyRef]]], _: RequestContext))
        .expects(*, *, *, *).returning(new Response()).anyNumberOfTimes()
      // ASSERTION: enrolment (c1,b1) -> progress=1, status=1 (leaf1 done of 2), and NO contentstatus column (content = resolve-on-read)
      (ops.updateRecordV2(_: String, _: String, _: util.Map[String, AnyRef], _: util.Map[String, AnyRef], _: Boolean, _: RequestContext))
        .expects(where { (_: String, _: String, sel: util.Map[String, AnyRef], upd: util.Map[String, AnyRef], _: Boolean, _: RequestContext) =>
          sel.get("courseid") == "c1" && sel.get("batchid") == "b1" &&
          upd.get("progress") == Integer.valueOf(1) && upd.get("status") == Integer.valueOf(1) && !upd.containsKey("contentstatus") })
        .returning(new Response()).once()
      val req = new Request; req.setOperation("aggregate")
      req.put("userId", "u1"); req.put("courseId", "leaf1"); req.put("batchId", "leaf1"); req.put("contentId", "leaf1")
      val result = callActor(req, Props(new ViewerAggregatorActor().configure(ops, hru)))
      result should not be null
    }
  }

  "aggregate in noContext collection mode" should "fan out to every active batch of the collection and DO store contentstatus" in {
    withMode("noContext", "collection") {
      val ops = mock[CassandraOperation]
      val hru = mock[HierarchyRelationsUtil]
      (hru.getLeafNodes(_: String, _: String, _: RequestContext)).expects("c1", "c1", *).returning(List("leaf1", "leaf2")).anyNumberOfTimes()
      (hru.getAncestors(_: String, _: String, _: RequestContext)).expects("c1", *, *).returning(List("c1")).anyNumberOfTimes()
      (hru.getOptionalNodes(_: String, _: String, _: RequestContext)).expects("c1", "c1", *).returning(List()).anyNumberOfTimes()
      // enrolments {userid} -> two active batches b1, b2 of c1
      (ops.getRecords(_: String, _: String, _: util.Map[String, AnyRef], _: util.List[String], _: RequestContext))
        .expects(where { (_: String, _: String, f: util.Map[String, AnyRef], _: util.List[String], _: RequestContext) =>
          f.containsKey("userid") && !f.containsKey("courseid") && !f.containsKey("collectionid") })
        .returning(rowsOf(enrol("c1", "b1", 1), enrol("c1", "b2", 1))).anyNumberOfTimes()
      (ops.getRecords(_: String, _: String, _: util.Map[String, AnyRef], _: util.List[String], _: RequestContext))
        .expects(where { (_: String, _: String, f: util.Map[String, AnyRef], _: util.List[String], _: RequestContext) => f.containsKey("courseid") })
        .returning(rowsOf(enrol("c1", "b1", 1))).anyNumberOfTimes()
      // (collection, collection) slice -> leaf1 done
      (ops.getRecords(_: String, _: String, _: util.Map[String, AnyRef], _: util.List[String], _: RequestContext))
        .expects(where { (_: String, _: String, f: util.Map[String, AnyRef], _: util.List[String], _: RequestContext) =>
          f.containsKey("collectionid") && f.get("collectionid") == "c1" && !f.containsKey("contentid") })
        .returning(rowsOf(uccLeaf("leaf1", 2))).anyNumberOfTimes()
      (ops.batchUpdateWithPutAll(_: String, _: String, _: util.List[util.Map[String, util.Map[String, AnyRef]]], _: RequestContext))
        .expects(*, *, *, *).returning(new Response()).anyNumberOfTimes()
      // both batches get updated, each WITH a contentstatus map (collection = materialized)
      (ops.updateRecordV2(_: String, _: String, _: util.Map[String, AnyRef], _: util.Map[String, AnyRef], _: Boolean, _: RequestContext))
        .expects(where { (_: String, _: String, sel: util.Map[String, AnyRef], upd: util.Map[String, AnyRef], _: Boolean, _: RequestContext) =>
          sel.get("batchid") == "b1" && upd.containsKey("contentstatus") && upd.get("progress") == Integer.valueOf(1) })
        .returning(new Response()).once()
      (ops.updateRecordV2(_: String, _: String, _: util.Map[String, AnyRef], _: util.Map[String, AnyRef], _: Boolean, _: RequestContext))
        .expects(where { (_: String, _: String, sel: util.Map[String, AnyRef], upd: util.Map[String, AnyRef], _: Boolean, _: RequestContext) =>
          sel.get("batchid") == "b2" && upd.containsKey("contentstatus") && upd.get("progress") == Integer.valueOf(1) })
        .returning(new Response()).once()
      val req = new Request; req.setOperation("aggregate")
      req.put("userId", "u1"); req.put("courseId", "c1"); req.put("batchId", "c1"); req.put("contentId", "leaf1")
      val result = callActor(req, Props(new ViewerAggregatorActor().configure(ops, hru)))
      result should not be null
    }
  }

  "aggregate in noContext mode" should "skip the recompute (no writes) when the target enrolment is already complete" in {
    withMode("noContext", "content") {
      val ops = mock[CassandraOperation]
      val hru = mock[HierarchyRelationsUtil]
      (hru.getLeafNodes(_: String, _: String, _: RequestContext)).expects("c1", "c1", *).returning(List("leaf1", "leaf2")).anyNumberOfTimes()
      // one active enrolment, already status=2
      (ops.getRecords(_: String, _: String, _: util.Map[String, AnyRef], _: util.List[String], _: RequestContext))
        .expects(where { (_: String, _: String, f: util.Map[String, AnyRef], _: util.List[String], _: RequestContext) =>
          f.containsKey("userid") && !f.containsKey("courseid") && !f.containsKey("collectionid") })
        .returning(rowsOf(enrol("c1", "b1", 2))).anyNumberOfTimes()
      // NO updateRecordV2 / batchUpdateWithPutAll / leaf-read expectations -> any such call fails the strict mock (proves the guard skipped)
      val req = new Request; req.setOperation("aggregate")
      req.put("userId", "u1"); req.put("courseId", "leaf1"); req.put("batchId", "leaf1"); req.put("contentId", "leaf1")
      val result = callActor(req, Props(new ViewerAggregatorActor().configure(ops, hru)))
      result should not be null
    }
  }
}
