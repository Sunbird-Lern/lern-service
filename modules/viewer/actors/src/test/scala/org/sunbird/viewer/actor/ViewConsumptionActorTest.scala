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
 * Unit tests for ViewConsumptionActor — view lifecycle (start/update/end/read) + assessment
 * (submit no-events branch + read). CassandraOperation is mocked via the setCassandraOperation seam;
 * the aggregator is a stub actor that replies immediately so the sync ask in viewEnd/viewAssess
 * returns fast. Scoring math itself is covered by AssessmentServiceSpec, not re-tested here.
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
    put("userid", "u1"); put("collectionid", "c1"); put("contextid", "b1"); put("contentid", "ct1")
    put("status", Integer.valueOf(status))
  }}

  private def callActor(request: Request, props: Props): Response = {
    val probe = new TestKit(system)
    val actorRef = system.actorOf(props)
    actorRef.tell(request, probe.testActor)
    probe.expectMsgType[Response](FiniteDuration.apply(15, TimeUnit.SECONDS))
  }

  private def viewRequest(op: String): Request = {
    val req = new Request
    req.setOperation(op)
    req.put("userId", "u1"); req.put("collectionId", "c1"); req.put("contextId", "b1"); req.put("contentId", "ct1")
    req
  }

  "viewStart" should "insert a new ucc row when absent" in {
    val ops = mock[CassandraOperation]
    (ops.getRecords(_: String, _: String, _: util.Map[String, AnyRef], _: util.List[String], _: RequestContext))
      .expects(*, *, *, *, *).returns(emptyRows)
    (ops.upsertRecord(_: String, _: String, _: util.Map[String, AnyRef], _: RequestContext))
      .expects(*, *, *, *).returns(new Response()).once()
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
    val result = callActor(viewRequest("viewStart"),
      Props(new ViewConsumptionActor(replyingAggregator).setCassandraOperation(ops)))
    result should not be null
  }

  "viewUpdate" should "upsert when the row exists and is not completed" in {
    val ops = mock[CassandraOperation]
    val rows = new util.ArrayList[util.Map[String, AnyRef]](); rows.add(uccRow(1))
    (ops.getRecords(_: String, _: String, _: util.Map[String, AnyRef], _: util.List[String], _: RequestContext))
      .expects(*, *, *, *, *).returns(rowsWith(rows))
    (ops.upsertRecord(_: String, _: String, _: util.Map[String, AnyRef], _: RequestContext))
      .expects(*, *, *, *).returns(new Response()).once()
    val result = callActor(viewRequest("viewUpdate"),
      Props(new ViewConsumptionActor(replyingAggregator).setCassandraOperation(ops)))
    result should not be null
  }

  "viewEnd" should "write status=2 and trigger the aggregation" in {
    val ops = mock[CassandraOperation]
    (ops.upsertRecord(_: String, _: String, _: util.Map[String, AnyRef], _: RequestContext))
      .expects(*, *, *, *).returns(new Response()).once()
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

  "viewAssess" should "mark the content complete and trigger aggregation when no events are sent" in {
    val ops = mock[CassandraOperation]
    (ops.upsertRecord(_: String, _: String, _: util.Map[String, AnyRef], _: RequestContext))
      .expects(*, *, *, *).returns(new Response()).once()
    val result = callActor(viewRequest("viewAssess"),
      Props(new ViewConsumptionActor(replyingAggregator).setCassandraOperation(ops)))
    result.getResult.get("ct1") shouldBe "SUCCESS"
  }

  "assessmentRead" should "return best score/max score per content" in {
    val ops = mock[CassandraOperation]
    val attempts = new util.ArrayList[util.Map[String, AnyRef]]()
    attempts.add(new util.HashMap[String, AnyRef]() {{
      put("attempt_id", "a1"); put("content_id", "ct1")
      put("total_score", java.lang.Double.valueOf(6.0)); put("total_max_score", java.lang.Double.valueOf(10.0))
    }})
    attempts.add(new util.HashMap[String, AnyRef]() {{
      put("attempt_id", "a2"); put("content_id", "ct1")
      put("total_score", java.lang.Double.valueOf(8.0)); put("total_max_score", java.lang.Double.valueOf(10.0))
    }})
    (ops.getRecordsByProperties(_: String, _: String, _: util.Map[String, AnyRef], _: util.List[String], _: RequestContext))
      .expects(*, *, *, *, *).returns(rowsWith(attempts))
    val result = callActor(viewRequest("assessmentRead"),
      Props(new ViewConsumptionActor(replyingAggregator).setCassandraOperation(ops)))
    val contents = result.getResult.get("contents").asInstanceOf[util.List[util.Map[String, AnyRef]]]
    contents.size() shouldBe 1
    contents.get(0).get("identifier") shouldBe "ct1"
    // best attempt (8.0) wins
    contents.get(0).get("score").asInstanceOf[Double] shouldBe 8.0
  }
}
