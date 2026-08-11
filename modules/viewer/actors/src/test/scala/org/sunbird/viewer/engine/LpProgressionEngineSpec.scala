package org.sunbird.viewer.engine

import org.scalamock.scalatest.MockFactory
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.sunbird.activity.util.{LpMeta, LpPolicyUtil, NodeMeta}
import org.sunbird.assessment.service.CassandraService
import org.sunbird.cassandra.CassandraOperation
import org.sunbird.request.RequestContext
import org.sunbird.response.Response

import java.util
import scala.collection.JavaConverters._
import scala.collection.mutable.ListBuffer

class LpProgressionEngineSpec extends AnyFlatSpec with Matchers with MockFactory {

  private val ctx = new RequestContext()

  // 2-level LP: crsA under L1, crsB under L2 (ancestors nearest-first, root LAST).
  private val ancestorsOf: String => List[String] = {
    case "crsA" => List("L1", "lp")
    case "crsB" => List("L2", "lp")
    case _      => Nil
  }
  private val trackable = List("crsA", "crsB")

  private def emptyRows: Response = { val r = new Response(); r.put("response", new util.ArrayList[util.Map[String, AnyRef]]()); r }
  private def optionalRows(opt: String*): Response = {
    val row = new util.HashMap[String, AnyRef](); row.put("optional_nodes", opt.toList.asJava)
    val list = new util.ArrayList[util.Map[String, AnyRef]](); list.add(row)
    val r = new Response(); r.put("response", list); r
  }

  /** Fake transport: records (user, course, batch) instead of messaging/HTTP. */
  private class FakeDispatcher extends EnrolDispatcher {
    val enrolled: ListBuffer[(String, String, String)] = ListBuffer.empty
    override def enrol(userId: String, courseId: String, batchId: String, ctx: RequestContext): Unit =
      enrolled += ((userId, courseId, batchId))
  }

  private def engineWith(ops: CassandraOperation, lp: LpPolicyUtil, disp: EnrolDispatcher): LpProgressionEngine =
    new LpProgressionEngine(ops, "ks", "user_enrolments", lp, new CassandraService(Some(ops)), disp)

  "advance (Strict)" should "open the first level's required course and gate the next level" in {
    val ops = mock[CassandraOperation]
    val lp = mock[LpPolicyUtil]
    val disp = new FakeDispatcher
    // optional_nodes read -> empty (not yet computed); Strict writes an empty optional set.
    (ops.getRecords(_: String, _: String, _: util.Map[String, AnyRef], _: util.List[String], _: RequestContext))
      .expects(*, *, *, *, *).returns(emptyRows).anyNumberOfTimes()
    (ops.updateRecordV2(_: String, _: String, _: util.Map[String, AnyRef], _: util.Map[String, AnyRef], _: Boolean, _: RequestContext))
      .expects(*, *, *, *, *, *).returns(new Response()).anyNumberOfTimes()
    (lp.lpMeta(_: String, _: RequestContext)).expects(*, *).returns(LpMeta("Strict", "", "", Map.empty[String, NodeMeta])).anyNumberOfTimes()
    (lp.policyOf(_: LpMeta)).expects(*).returns("Strict").anyNumberOfTimes()

    engineWith(ops, lp, disp).advance("uA", "lp", "bA", trackable, Map.empty, ancestorsOf, ctx)

    disp.enrolled.toList shouldBe List(("uA", "crsA", "bA:crsA")) // first level's course only; crsB (L2) gated
  }

  "advance" should "skip a waived course and open the next level's course" in {
    val ops = mock[CassandraOperation]
    val lp = mock[LpPolicyUtil]
    val disp = new FakeDispatcher
    // optional_nodes already = [crsA] -> optionality is 'computed' (non-empty), so ensureOptionality
    // returns immediately; crsA is treated as waived, L1 is complete, L2's crsB opens.
    (ops.getRecords(_: String, _: String, _: util.Map[String, AnyRef], _: util.List[String], _: RequestContext))
      .expects(*, *, *, *, *).returns(optionalRows("crsA")).anyNumberOfTimes()

    engineWith(ops, lp, disp).advance("uB", "lp", "bB", trackable, Map.empty, ancestorsOf, ctx)

    disp.enrolled.toList shouldBe List(("uB", "crsB", "bB:crsB")) // crsA waived -> next required course opens
  }

  "advance (Adaptive, no pre-assessment)" should "halt and open nothing (misconfigured)" in {
    val ops = mock[CassandraOperation]
    val lp = mock[LpPolicyUtil]
    val disp = new FakeDispatcher
    // optionality not yet computed; Adaptive policy; no course is an assessment -> no pre-assessment -> halt.
    (ops.getRecords(_: String, _: String, _: util.Map[String, AnyRef], _: util.List[String], _: RequestContext))
      .expects(*, *, *, *, *).returns(emptyRows).anyNumberOfTimes()
    (lp.lpMeta(_: String, _: RequestContext)).expects(*, *).returns(LpMeta("Adaptive", "", "", Map.empty[String, NodeMeta])).anyNumberOfTimes()
    (lp.policyOf(_: LpMeta)).expects(*).returns("Adaptive").anyNumberOfTimes()
    (lp.isAssessmentCourse(_: String, _: LpMeta)).expects(*, *).returns(false).anyNumberOfTimes()

    engineWith(ops, lp, disp).advance("uC", "lp", "bC", trackable, Map.empty, ancestorsOf, ctx)

    disp.enrolled shouldBe empty // misconfigured Adaptive LP opens no course (no updateRecordV2 either)
  }
}
