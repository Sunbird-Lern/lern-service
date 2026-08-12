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

  // resolveParentLp: the completion trigger's parent-lookup. Child batch = "LPbatch:childId"; the LP is the
  // course_batch row keyed by the stripped LPbatch whose courseid differs from the completed course.
  private def batchRow(courseId: String): util.Map[String, AnyRef] =
    new util.HashMap[String, AnyRef]() {{ put("courseId", courseId) }}
  private def batchRows(courseIds: String*): util.List[util.Map[String, AnyRef]] = {
    val l = new util.ArrayList[util.Map[String, AnyRef]](); courseIds.foreach(c => l.add(batchRow(c))); l
  }

  "resolveParentLp" should "return None for a standalone (colon-free) batch without fetching" in {
    var fetched = false
    val out = ViewerAggregatorActor.resolveParentLp("course-1", "plainBatch", _ => { fetched = true; batchRows() })
    out shouldBe None
    fetched shouldBe false // standalone course -> no course_batch read, no trigger
  }

  "resolveParentLp" should "resolve the LP course + stripped LP batch from the child batch" in {
    val out = ViewerAggregatorActor.resolveParentLp(
      "course-1", "lpBatch-9:course-1", lpBatch => { lpBatch shouldBe "lpBatch-9"; batchRows("course-1", "lp-root") })
    out shouldBe Some(("lp-root", "lpBatch-9")) // the row whose courseid != the completed course
  }

  "resolveParentLp" should "return None when the batch maps only to the course itself" in {
    // course consumed under its own colon-batch, but course_batch has no differing (LP) row -> not an LP child
    val out = ViewerAggregatorActor.resolveParentLp("course-1", "lpBatch-9:course-1", _ => batchRows("course-1"))
    out shouldBe None
  }

  "resolveParentLp" should "pick the first differing course id when several rows share the LP batch" in {
    // In the live schema only the LP-root row is keyed by the bare lpBatch (children are lpBatch:childId), so
    // this is a defensive/bad-data case: with multiple differing rows the resolver is first-match-wins.
    val out = ViewerAggregatorActor.resolveParentLp("course-1", "lpBatch-9:course-1",
      _ => batchRows("course-1", "lp-root", "lp-other"))
    out shouldBe Some(("lp-root", "lpBatch-9"))
  }

  // parseCourseCertEnabled: course certs default ON; only literal "false" disables (LP cert unaffected).
  "parseCourseCertEnabled" should "default to true when unset/blank and honor an explicit false" in {
    ViewerAggregatorActor.parseCourseCertEnabled(null) shouldBe true
    ViewerAggregatorActor.parseCourseCertEnabled("") shouldBe true
    ViewerAggregatorActor.parseCourseCertEnabled("true") shouldBe true
    ViewerAggregatorActor.parseCourseCertEnabled("FALSE") shouldBe false
  }
}
