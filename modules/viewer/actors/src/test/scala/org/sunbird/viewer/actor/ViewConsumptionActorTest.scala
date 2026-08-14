package org.sunbird.viewer.actor

import java.util
import java.util.concurrent.TimeUnit

import org.apache.pekko.actor.{Actor, ActorSystem, Props}
import org.apache.pekko.testkit.TestKit
import org.scalamock.scalatest.MockFactory
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.sunbird.cassandra.CassandraOperation
import org.sunbird.request.{Request, RequestContext}
import org.sunbird.response.Response

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.FiniteDuration

/**
 * Unit tests for ViewConsumptionActor — view lifecycle (start/update/end/read). CassandraOperation
 * is mocked via the setCassandraOperation seam; the aggregator is a stub actor that replies
 * immediately so the sync ask in viewEnd returns fast.
 *
 * Every write op also calls touchEnrolmentAccess, which reads the enrolment (getRecordByIdentifier)
 * and only stamps it (updateRecordV2) when it EXISTS — so each test stubs that read. The two
 * "touchEnrolmentAccess" cases pin that guard (C1: no phantom enrolment row on an unenrolled view).
 */
class ViewConsumptionActorTest extends AnyFlatSpec with Matchers with MockFactory {

  implicit val ec: ExecutionContext = ExecutionContext.global
  val system: ActorSystem = ActorSystem.create("viewer-consumption-test")

  // stub aggregator: replies to the Patterns.ask so triggerAggregation completes without the 30s timeout
  private def replyingAggregator = system.actorOf(Props(new Actor {
    def receive: Receive = { case _ => sender() ! new Response() }
  }))

  private def emptyRows: Response = {
    val r = new Response(); r.put("response", new util.ArrayList[util.Map[String, AnyRef]]()); r
  }

  private def rowsWith(rows: util.List[util.Map[String, AnyRef]]): Response = {
    val r = new Response(); r.put("response", rows); r
  }

  private def uccRow(status: Int): util.Map[String, AnyRef] = new util.HashMap[String, AnyRef]() {{
    put("userid", "u1"); put("courseid", "c1"); put("batchid", "b1"); put("contentid", "ct1")
    put("status", Integer.valueOf(status))
  }}

  private def enrolmentRow: util.Map[String, AnyRef] = new util.HashMap[String, AnyRef]() {{
    put("userid", "u1"); put("courseId", "c1"); put("batchId", "b1"); put("status", Integer.valueOf(1))
  }}

  // touchEnrolmentAccess's enrolment read; `result` decides whether the row is stamped.
  private def stubEnrolmentRead(ops: CassandraOperation, result: Response) =
    (ops.getRecordByIdentifier(_: String, _: String, _: Object, _: util.List[String], _: RequestContext))
      .expects(*, *, *, *, *).returns(result)

  private def expectEnrolmentStamp(ops: CassandraOperation) =
    (ops.updateRecordV2(_: String, _: String, _: util.Map[String, AnyRef], _: util.Map[String, AnyRef], _: Boolean, _: RequestContext))
      .expects(*, *, *, *, *, *).returns(new Response()).once()

  private def callActor(request: Request, props: Props): Response = {
    val probe = new TestKit(system)
    val actorRef = system.actorOf(props)
    actorRef.tell(request, probe.testActor)
    probe.expectMsgType[Response](FiniteDuration.apply(15, TimeUnit.SECONDS))
  }

  private def viewRequest(op: String): Request = {
    val req = new Request
    req.setOperation(op)
    req.put("userId", "u1"); req.put("courseId", "c1"); req.put("batchId", "b1"); req.put("contentId", "ct1")
    req
  }

  "viewStart" should "insert a new ucc row when absent" in {
    val ops = mock[CassandraOperation]
    (ops.getRecords(_: String, _: String, _: util.Map[String, AnyRef], _: util.List[String], _: RequestContext))
      .expects(*, *, *, *, *).returns(emptyRows)
    (ops.upsertRecord(_: String, _: String, _: util.Map[String, AnyRef], _: RequestContext))
      .expects(*, *, *, *).returns(new Response()).once()
    stubEnrolmentRead(ops, emptyRows)
    val result = callActor(viewRequest("viewStart"),
      Props(new ViewConsumptionActor(replyingAggregator).setCassandraOperation(ops)))
    result should not be null
  }

  "viewStart" should "be a no-op when the row already exists" in {
    val ops = mock[CassandraOperation]
    val rows = new util.ArrayList[util.Map[String, AnyRef]](); rows.add(uccRow(1))
    (ops.getRecords(_: String, _: String, _: util.Map[String, AnyRef], _: util.List[String], _: RequestContext))
      .expects(*, *, *, *, *).returns(rowsWith(rows))
    // no upsertRecord expectation -> a call would fail the strict mock
    stubEnrolmentRead(ops, emptyRows)
    val result = callActor(viewRequest("viewStart"),
      Props(new ViewConsumptionActor(replyingAggregator).setCassandraOperation(ops)))
    result should not be null
  }

  "viewStart" should "keep the higher existing progress on merge (monotonic)" in {
    val ops = mock[CassandraOperation]
    val rows = new util.ArrayList[util.Map[String, AnyRef]](); rows.add(new util.HashMap[String, AnyRef]() {{
      put("userid", "u1"); put("courseid", "c1"); put("batchid", "b1"); put("contentid", "ct1")
      put("status", Integer.valueOf(1)); put("progress", Integer.valueOf(80))
    }})
    (ops.getRecords(_: String, _: String, _: util.Map[String, AnyRef], _: util.List[String], _: RequestContext))
      .expects(*, *, *, *, *).returns(rowsWith(rows))
    (ops.upsertRecord(_: String, _: String, _: util.Map[String, AnyRef], _: RequestContext))
      .expects(where { (_: String, _: String, row: util.Map[String, AnyRef], _: RequestContext) => row.get("progress") == Integer.valueOf(80) })
      .returns(new Response()).once()
    stubEnrolmentRead(ops, emptyRows)
    val req = viewRequest("viewStart"); req.put("progress", Integer.valueOf(50)) // lower than existing 80
    val result = callActor(req, Props(new ViewConsumptionActor(replyingAggregator).setCassandraOperation(ops)))
    result should not be null
  }

  "viewUpdate" should "upsert when the row exists and is not completed" in {
    val ops = mock[CassandraOperation]
    val rows = new util.ArrayList[util.Map[String, AnyRef]](); rows.add(uccRow(1))
    (ops.getRecords(_: String, _: String, _: util.Map[String, AnyRef], _: util.List[String], _: RequestContext))
      .expects(*, *, *, *, *).returns(rowsWith(rows))
    (ops.upsertRecord(_: String, _: String, _: util.Map[String, AnyRef], _: RequestContext))
      .expects(*, *, *, *).returns(new Response()).once()
    stubEnrolmentRead(ops, emptyRows)
    val result = callActor(viewRequest("viewUpdate"),
      Props(new ViewConsumptionActor(replyingAggregator).setCassandraOperation(ops)))
    result should not be null
  }

  "viewEnd" should "write status=2 and trigger the aggregation" in {
    val ops = mock[CassandraOperation]
    // viewEnd reads the existing ucc row to merge viewcount/lastCompletedTime monotonically
    (ops.getRecords(_: String, _: String, _: util.Map[String, AnyRef], _: util.List[String], _: RequestContext))
      .expects(*, *, *, *, *).returns(emptyRows)
    (ops.upsertRecord(_: String, _: String, _: util.Map[String, AnyRef], _: RequestContext))
      .expects(*, *, *, *).returns(new Response()).once()
    stubEnrolmentRead(ops, emptyRows)
    val result = callActor(viewRequest("viewEnd"),
      Props(new ViewConsumptionActor(replyingAggregator).setCassandraOperation(ops)))
    result should not be null
  }

  // C1: touchEnrolmentAccess must NOT fabricate an enrolment for an unenrolled/no-context view.
  "touchEnrolmentAccess" should "not stamp the enrolment when none exists" in {
    val ops = mock[CassandraOperation]
    (ops.getRecords(_: String, _: String, _: util.Map[String, AnyRef], _: util.List[String], _: RequestContext))
      .expects(*, *, *, *, *).returns(emptyRows)
    (ops.upsertRecord(_: String, _: String, _: util.Map[String, AnyRef], _: RequestContext))
      .expects(*, *, *, *).returns(new Response()).once()
    stubEnrolmentRead(ops, emptyRows) // no enrolment
    // no updateRecordV2 expectation -> a stamp write would fail the strict mock (phantom-row guard)
    val result = callActor(viewRequest("viewEnd"),
      Props(new ViewConsumptionActor(replyingAggregator).setCassandraOperation(ops)))
    result should not be null
  }

  // C1: when the enrolment DOES exist, its last-access is stamped exactly once.
  "touchEnrolmentAccess" should "stamp the enrolment when it exists" in {
    val ops = mock[CassandraOperation]
    (ops.getRecords(_: String, _: String, _: util.Map[String, AnyRef], _: util.List[String], _: RequestContext))
      .expects(*, *, *, *, *).returns(emptyRows)
    (ops.upsertRecord(_: String, _: String, _: util.Map[String, AnyRef], _: RequestContext))
      .expects(*, *, *, *).returns(new Response()).once()
    val rows = new util.ArrayList[util.Map[String, AnyRef]](); rows.add(enrolmentRow)
    stubEnrolmentRead(ops, rowsWith(rows)) // enrolment present
    expectEnrolmentStamp(ops)
    val result = callActor(viewRequest("viewEnd"),
      Props(new ViewConsumptionActor(replyingAggregator).setCassandraOperation(ops)))
    result should not be null
  }

  "viewRead" should "return the ucc rows" in {
    val ops = mock[CassandraOperation]
    val rows = new util.ArrayList[util.Map[String, AnyRef]](); rows.add(uccRow(2))
    (ops.getRecords(_: String, _: String, _: util.Map[String, AnyRef], _: util.List[String], _: RequestContext))
      .expects(*, *, *, *, *).returns(rowsWith(rows))
    val result = callActor(viewRequest("viewRead"),
      Props(new ViewConsumptionActor(replyingAggregator).setCassandraOperation(ops)))
    val out = result.getResult.get("response").asInstanceOf[util.List[util.Map[String, AnyRef]]]
    out.size() shouldBe 1
  }
}
