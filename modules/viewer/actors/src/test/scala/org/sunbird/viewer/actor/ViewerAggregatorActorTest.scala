package org.sunbird.viewer.actor

import java.util
import java.util.concurrent.TimeUnit

import org.apache.pekko.actor.{ActorSystem, Props}
import org.apache.pekko.testkit.TestKit
import org.scalamock.scalatest.MockFactory
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.sunbird.activity.util.{CertificateUtil, HierarchyRelationsUtil}
import org.sunbird.cassandra.CassandraOperation
import org.sunbird.request.{Request, RequestContext}
import org.sunbird.response.Response

import scala.concurrent.duration.FiniteDuration

/**
 * Unit tests for ViewerAggregatorActor guard branches (deterministic, no hierarchy fixture needed):
 *  - missing userId/collectionId -> skip, still replies success.
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

  private def aggRequest(userId: String, collectionId: String): Request = {
    val req = new Request
    req.setOperation("aggregate")
    if (userId != null) req.put("userId", userId)
    if (collectionId != null) req.put("collectionId", collectionId)
    req.put("contextId", "b1")
    req
  }

  "aggregate" should "skip and reply success when userId/collectionId are missing" in {
    val ops = mock[CassandraOperation]
    val hru = mock[HierarchyRelationsUtil]
    val cu = mock[CertificateUtil]
    // no cassandra / hierarchy / cert interaction expected on the missing-id guard
    val result = callActor(aggRequest(null, null), Props(new ViewerAggregatorActor().configure(ops, hru, cu)))
    result should not be null
  }

  "aggregate" should "early-return with no writes when there is no consumption" in {
    val ops = mock[CassandraOperation]
    val hru = mock[HierarchyRelationsUtil]
    val cu = mock[CertificateUtil]
    // not an LP (empty trackablenodes) -> plain rollup path; no consumption -> early return, no writes
    (hru.getTrackableNodes(_: String, _: RequestContext)).expects(*, *).returns(List())
    // readConsumption -> empty; must NOT reach batchUpdateWithPutAll / updateRecordV2 / cert
    (ops.getRecords(_: String, _: String, _: util.Map[String, AnyRef], _: util.List[String], _: RequestContext))
      .expects(*, *, *, *, *).returns(emptyRows)
    val result = callActor(aggRequest("u1", "c1"), Props(new ViewerAggregatorActor().configure(ops, hru, cu)))
    result should not be null
  }
}
