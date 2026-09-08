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
    // not an LP (empty trackablenodes) -> the LP bootstrap branch is skipped
    (hru.getTrackableNodes(_: String, _: RequestContext)).expects(*, *).returns(Nil)
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

  // ---- leafViews: progress excludes waived leaves, contentStatus never does -----------------
  // Regression: contentStatus used to be filtered to the progress set, so a learner who studied a
  // WAIVED course had their completions recorded in user_content_consumption but omitted from
  // user_enrolments.contentstatus -- /v1/summary/read never mentioned them and the portal showed
  // "0/2 - 0%" with unticked leaves for ever.

  "leafViews" should "keep both views identical when nothing is waived" in {
    val leaves = List("leaf-a", "leaf-b", "leaf-c")
    val v = ViewerAggregatorActor.leafViews(leaves, Set.empty)
    v.required shouldBe leaves
    v.all shouldBe leaves
    // the unwaived path must be byte-identical to the pre-fix behaviour
    v.required shouldBe v.all
  }

  it should "exclude waived leaves from progress but keep them in the contentStatus view" in {
    val leaves = List("leaf-a", "leaf-waived-1", "leaf-b", "leaf-waived-2")
    val v = ViewerAggregatorActor.leafViews(leaves, Set("leaf-waived-1", "leaf-waived-2"))
    // progress denominator: only what the learner is still required to do
    v.required shouldBe List("leaf-a", "leaf-b")
    // factual record: every leaf, so a waived leaf the learner chose to finish still shows up
    v.all shouldBe leaves
  }

  it should "report an empty required set when every leaf is waived, without losing the leaves" in {
    val leaves = List("leaf-a", "leaf-b")
    val v = ViewerAggregatorActor.leafViews(leaves, Set("leaf-a", "leaf-b"))
    v.required shouldBe empty      // nothing left to require -> node cannot be pinned below 100%
    v.all shouldBe leaves          // but completions remain recordable
  }

  it should "ignore optional ids that are not leaves of this node" in {
    val leaves = List("leaf-a", "leaf-b")
    val v = ViewerAggregatorActor.leafViews(leaves, Set("some-other-course", "leaf-b"))
    v.required shouldBe List("leaf-a")
    v.all shouldBe leaves
  }

  it should "record a completed WAIVED leaf in contentStatus" in {
    val leaves = List("leaf-required", "leaf-waived")
    val v = ViewerAggregatorActor.leafViews(leaves, Set("leaf-waived"))
    // the learner finished both; contentStatus is built from `all`, as the actor now does
    val statuses = Map("leaf-required" -> Integer.valueOf(2).asInstanceOf[AnyRef],
                       "leaf-waived" -> Integer.valueOf(2).asInstanceOf[AnyRef])
    val fresh = v.all.flatMap(l => statuses.get(l).map(st => l -> st)).toMap
    val merged = ViewerAggregatorActor.mergeContentStatus(null, fresh)
    merged.get("leaf-waived") shouldBe Integer.valueOf(2)  // the bug: previously absent
    merged.size() shouldBe 2
    // and the waived leaf still does not inflate the progress denominator
    v.required shouldBe List("leaf-required")
  }
}
