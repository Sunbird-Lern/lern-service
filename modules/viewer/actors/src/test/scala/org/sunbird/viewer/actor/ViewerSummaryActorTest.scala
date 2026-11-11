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
import org.sunbird.exception.ProjectCommonException
import org.sunbird.request.{Request, RequestContext}
import org.sunbird.response.Response

import scala.concurrent.duration.FiniteDuration

// Summary APIs — spec shapes: summary.read = single enriched object, summary.list = { summary:[…] },
// summary.delete = { userId -> ack }. Collection/assessment enrichment is fail-safe (empty here, no content service).
class ViewerSummaryActorTest extends AnyFlatSpec with Matchers with MockFactory {

  val system: ActorSystem = ActorSystem.create("viewer-summary-test")

  private def rowsResp(rs: util.List[util.Map[String, AnyRef]]): Response = {
    val r = new Response; r.put("response", rs); r
  }

  private def enrolmentRows: util.List[util.Map[String, AnyRef]] = {
    val l = new util.ArrayList[util.Map[String, AnyRef]]()
    l.add(new util.HashMap[String, AnyRef]() {{
      put("userId", "u1"); put("courseId", "c1"); put("batchId", "b1")
      put("status", Integer.valueOf(2)); put("progress", Integer.valueOf(100))
      put("contentStatus", new util.HashMap[String, AnyRef]() {{ put("ct1", Integer.valueOf(2)) }})
    }})
    l
  }

  private def anyGetRecords(ops: CassandraOperation, resp: Response) =
    (ops.getRecords(_: String, _: String, _: util.Map[String, AnyRef], _: util.List[String], _: RequestContext))
      .expects(*, *, *, *, *).returning(resp).anyNumberOfTimes()

  private def callActor(request: Request, ops: CassandraOperation): Response = {
    val probe = new TestKit(system)
    val ref = system.actorOf(Props(new ViewerSummaryActor().setCassandraOperation(ops)))
    ref.tell(request, probe.testActor)
    probe.expectMsgType[Response](FiniteDuration.apply(15, TimeUnit.SECONDS))
  }

  "summaryRead" should "return a single enriched object (spec keys, no list wrapper)" in {
    val ops = mock[CassandraOperation]
    anyGetRecords(ops, rowsResp(enrolmentRows))
    val req = new Request; req.setOperation("summaryRead")
    req.put("userId", "u1"); req.put("courseId", "c1"); req.put("batchId", "b1")
    val res = callActor(req, ops).getResult
    res.get("collectionId") shouldBe "c1"
    res.get("contextId") shouldBe "b1"
    res.get("status") shouldBe Integer.valueOf(2)
    res.containsKey("response") shouldBe false
    res.containsKey("summary") shouldBe false
    res.get("contentStatus").asInstanceOf[util.Map[String, AnyRef]].get("ct1") shouldBe Integer.valueOf(2)
  }

  "summaryList" should "wrap enrolments under summary[]" in {
    val ops = mock[CassandraOperation]
    anyGetRecords(ops, rowsResp(enrolmentRows))
    val req = new Request; req.setOperation("summaryList"); req.put("userId", "u1")
    val res = callActor(req, ops).getResult
    val summary = res.get("summary").asInstanceOf[util.List[util.Map[String, AnyRef]]]
    summary.size() shouldBe 1
    summary.get(0).get("collectionId") shouldBe "c1"
  }

  "summaryRead" should "reject when contextId is missing (collectionId + contextId are mandatory)" in {
    val ops = mock[CassandraOperation] // no getRecords expected — validation throws first
    val req = new Request; req.setOperation("summaryRead"); req.put("userId", "u1"); req.put("courseId", "c1")
    val probe = new TestKit(system)
    val ref = system.actorOf(Props(new ViewerSummaryActor().setCassandraOperation(ops)))
    ref.tell(req, probe.testActor)
    probe.expectMsgType[ProjectCommonException](FiniteDuration.apply(15, TimeUnit.SECONDS))
  }

  // --- Phase 3: NoContext Content resolves contentStatus per-leaf on read (the stored column is empty in that mode) ---

  private def withMode[T](mode: String, scope: String)(body: => T): T = {
    val cache = PropertiesCache.getInstance()
    cache.saveConfigProperty("viewer_context_mode", mode); cache.saveConfigProperty("viewer_carry_scope", scope)
    try body finally { cache.saveConfigProperty("viewer_context_mode", "strict"); cache.saveConfigProperty("viewer_carry_scope", "content") }
  }

  private def row(kv: (String, AnyRef)*): util.Map[String, AnyRef] = { val m = new util.HashMap[String, AnyRef](); kv.foreach { case (k, v) => m.put(k, v) }; m }

  "summaryRead in noContext content mode" should "resolve contentStatus per-leaf from the (leaf,leaf,leaf) rows, not the stored column" in {
    withMode("noContext", "content") {
      val ops = mock[CassandraOperation]
      val hru = mock[HierarchyRelationsUtil]
      // course c1 has leaves leaf1, leaf2
      (hru.getLeafNodes(_: String, _: String, _: RequestContext)).expects("c1", "c1", *).returning(List("leaf1", "leaf2")).anyNumberOfTimes()
      // enrolment row: NO stored contentStatus, in-progress
      (ops.getRecords(_: String, _: String, _: util.Map[String, AnyRef], _: util.List[String], _: RequestContext))
        .expects(where { (_: String, _: String, f: util.Map[String, AnyRef], _: util.List[String], _: RequestContext) => f.containsKey("courseid") })
        .returning(rowsResp({ val l = new util.ArrayList[util.Map[String, AnyRef]](); l.add(row("userId" -> "u1", "courseId" -> "c1", "batchId" -> "b1", "status" -> Integer.valueOf(1), "progress" -> Integer.valueOf(50))); l })).anyNumberOfTimes()
      // consumption partition read {userid} -> leaf1 done at (leaf1,leaf1,leaf1)
      (ops.getRecords(_: String, _: String, _: util.Map[String, AnyRef], _: util.List[String], _: RequestContext))
        .expects(where { (_: String, _: String, f: util.Map[String, AnyRef], _: util.List[String], _: RequestContext) =>
          f.containsKey("userid") && !f.containsKey("courseid") && !f.containsKey("user_id") })
        .returning(rowsResp({ val l = new util.ArrayList[util.Map[String, AnyRef]](); l.add(row("collectionid" -> "leaf1", "contextid" -> "leaf1", "contentid" -> "leaf1", "status" -> Integer.valueOf(2))); l })).anyNumberOfTimes()
      // assessment IN query {user_id, collection_id} -> none
      (ops.getRecords(_: String, _: String, _: util.Map[String, AnyRef], _: util.List[String], _: RequestContext))
        .expects(where { (_: String, _: String, f: util.Map[String, AnyRef], _: util.List[String], _: RequestContext) => f.containsKey("user_id") })
        .returning(rowsResp(new util.ArrayList[util.Map[String, AnyRef]]())).anyNumberOfTimes()
      val probe = new TestKit(system)
      val ref = system.actorOf(Props(new ViewerSummaryActor().configure(ops, hru)))
      val req = new Request; req.setOperation("summaryRead"); req.put("userId", "u1"); req.put("courseId", "c1"); req.put("batchId", "b1")
      ref.tell(req, probe.testActor)
      val res = probe.expectMsgType[Response](FiniteDuration.apply(15, TimeUnit.SECONDS)).getResult
      val cs = res.get("contentStatus").asInstanceOf[util.Map[String, AnyRef]]
      cs.get("leaf1") shouldBe Integer.valueOf(2) // resolved from (leaf1,leaf1,leaf1)
      cs.get("leaf2") shouldBe Integer.valueOf(0) // not consumed
    }
  }

  "summaryDelete" should "purge multiple tables (not just the enrolment) and ack keyed by userId" in {
    val ops = mock[CassandraOperation]
    val tables = scala.collection.mutable.Set[String]()
    (ops.deleteRecord(_: String, _: String, _: util.Map[String, String], _: RequestContext))
      .expects(*, *, *, *).onCall { (_: String, t: String, _: util.Map[String, String], _: RequestContext) => tables += t; () }
      .anyNumberOfTimes()
    val req = new Request; req.setOperation("summaryDelete")
    req.put("userId", "u1"); req.put("courseId", "c1"); req.put("batchId", "b1")
    val res = callActor(req, ops).getResult
    res.get("u1") shouldBe "Enrolment Deleted Succesfully"
    res.get("purged").asInstanceOf[util.List[util.Map[String, AnyRef]]].size() shouldBe 1
    tables.size should be >= 3 // enrolment + consumption + assessment (+ activity_agg when configured)
  }

  // --- Phase 4: ownership-based purge — low-level rows dropped only when this enrolment owns them ---

  private def captureDeletes(ops: CassandraOperation): scala.collection.mutable.ArrayBuffer[(String, util.Map[String, String])] = {
    val deletes = scala.collection.mutable.ArrayBuffer[(String, util.Map[String, String])]()
    (ops.deleteRecord(_: String, _: String, _: util.Map[String, String], _: RequestContext))
      .expects(*, *, *, *).onCall { (_: String, t: String, k: util.Map[String, String], _: RequestContext) => deletes += ((t, k)); () }.anyNumberOfTimes()
    deletes
  }

  "summaryDelete in noContext content mode" should "keep the content-level rows (never drop them on un-enrol)" in {
    withMode("noContext", "content") {
      val ops = mock[CassandraOperation]
      val deletes = captureDeletes(ops)
      val req = new Request; req.setOperation("summaryDelete"); req.put("userId", "u1"); req.put("courseId", "c1"); req.put("batchId", "b1")
      callActor(req, ops)
      deletes.nonEmpty shouldBe true // enrolment + activity_agg still removed
      deletes.map(_._1) should not contain "user_content_consumption" // content rows survive
    }
  }

  "summaryDelete in noContext collection mode" should "drop the shared (collection,collection) rows only when it is the last active batch" in {
    withMode("noContext", "collection") {
      val ops = mock[CassandraOperation]
      // hasOtherActiveBatch -> only b1 exists => this is the last batch
      (ops.getRecords(_: String, _: String, _: util.Map[String, AnyRef], _: util.List[String], _: RequestContext))
        .expects(*, *, *, *, *).returning(rowsResp({ val l = new util.ArrayList[util.Map[String, AnyRef]](); l.add(row("courseId" -> "c1", "batchId" -> "b1", "active" -> java.lang.Boolean.TRUE)); l })).anyNumberOfTimes()
      val deletes = captureDeletes(ops)
      val req = new Request; req.setOperation("summaryDelete"); req.put("userId", "u1"); req.put("courseId", "c1"); req.put("batchId", "b1")
      callActor(req, ops)
      deletes.exists { case (t, k) => t == "user_content_consumption" && k.get("contextid") == "c1" } shouldBe true
    }
  }

  "summaryDelete in noContext collection mode" should "keep the shared rows when another active batch exists" in {
    withMode("noContext", "collection") {
      val ops = mock[CassandraOperation]
      // another active batch b2 exists => shared rows must survive
      (ops.getRecords(_: String, _: String, _: util.Map[String, AnyRef], _: util.List[String], _: RequestContext))
        .expects(*, *, *, *, *).returning(rowsResp({ val l = new util.ArrayList[util.Map[String, AnyRef]]()
          l.add(row("courseId" -> "c1", "batchId" -> "b1", "active" -> java.lang.Boolean.TRUE))
          l.add(row("courseId" -> "c1", "batchId" -> "b2", "active" -> java.lang.Boolean.TRUE)); l })).anyNumberOfTimes()
      val deletes = captureDeletes(ops)
      val req = new Request; req.setOperation("summaryDelete"); req.put("userId", "u1"); req.put("courseId", "c1"); req.put("batchId", "b1")
      callActor(req, ops)
      deletes.map(_._1) should not contain "user_content_consumption"
    }
  }
}
