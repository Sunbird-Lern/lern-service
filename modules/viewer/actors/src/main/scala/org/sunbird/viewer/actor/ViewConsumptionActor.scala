package org.sunbird.viewer.actor

import com.fasterxml.jackson.databind.ObjectMapper
import org.apache.commons.collections4.CollectionUtils
import org.apache.commons.lang3.StringUtils
import org.apache.pekko.actor.ActorRef
import org.sunbird.assessment.models._
import org.sunbird.assessment.service.{AssessmentService, CassandraService, ContentService}
import org.sunbird.assessment.util.AssessmentParser
import org.sunbird.common.ProjectUtil
import org.sunbird.enrolments.BaseEnrolmentActor
import org.sunbird.helper.ServiceFactory
import org.sunbird.keys.JsonKey
import org.sunbird.learner.util.Util
import org.sunbird.request.{Request, RequestContext}
import org.sunbird.response.Response

import java.util
import javax.inject.{Inject, Named}
import scala.collection.JavaConverters._

/**
 * Granular view lifecycle writes to user_content_consumption (ucc).
 *
 * Write model = read-modify-upsert with MONOTONIC merge (mirrors ContentConsumptionActor;
 * NOT Paxos LWT). Race-free by monotonicity + Cassandra per-cell LWW + per-userId serialization:
 *   viewStart  -> INSERT only if absent (status 1); if present, no-op.
 *   viewUpdate -> merge only if row exists; revisits (already status 2) ignored.
 *   viewEnd    -> status 2 + completed time; then async aggregation (fire-and-forget tell).
 *
 * ucc PK (viewer schema §2): (userid, courseid, batchid, contentid).
 * No collection context -> courseid = batchid = contentid.
 */
class ViewConsumptionActor @Inject() (
    @Named("viewer-aggregator-actor") viewerAggregatorActor: ActorRef
) extends BaseEnrolmentActor {

  private val mapper = new ObjectMapper
  private var cassandraOperation = ServiceFactory.getInstance
  private val consumptionDBInfo = Util.dbInfoMap.get(JsonKey.LEARNER_CONTENT_DB)
  private val CONSUMPTION_TABLE = "user_content_consumption"
  private val enrolmentDBInfo = Util.dbInfoMap.get(JsonKey.LEARNER_COURSE_DB)

  /**
   * Stamp the enrolment's last-content-access on every view op (mirrors standard content-consumption):
   * user_enrolments.lastcontentaccesstime/lastreadcontentid/lastreadcontentstatus. Keyed by the ucc
   * primary key (userid, courseid, batchid). This is what summary/list surfaces as access time.
   */
  private def touchEnrolmentAccess(key: util.HashMap[String, AnyRef], status: Int, ctx: RequestContext): Unit = {
    val selectMap = new util.HashMap[String, AnyRef]() {{
      put("userid", key.get("userid")); put("courseid", key.get("courseid")); put("batchid", key.get("batchid"))
    }}
    // Only stamp an EXISTING enrolment. updateRecordV2's ifExists is a no-op (plain UPDATE upserts in
    // Cassandra), so without this guard a no-context/unenrolled view would fabricate a phantom enrolment row.
    val existing = cassandraOperation.getRecordByIdentifier(enrolmentDBInfo.getKeySpace, enrolmentDBInfo.getTableName, selectMap, null, ctx)
      .getResult.getOrDefault(JsonKey.RESPONSE, new util.ArrayList[util.Map[String, AnyRef]]).asInstanceOf[util.List[util.Map[String, AnyRef]]]
    if (existing.isEmpty) return
    val updateMap = new util.HashMap[String, AnyRef]() {{
      put("lastcontentaccesstime", new java.util.Date())
      put("lastreadcontentid", key.get("contentid"))
      put("lastreadcontentstatus", Integer.valueOf(status))
    }}
    cassandraOperation.updateRecordV2(enrolmentDBInfo.getKeySpace, enrolmentDBInfo.getTableName, selectMap, updateMap, true, ctx)
  }

  // Assessment scoring reuses the assessment-aggregator services in-process (same math + persistence
  // the legacy AssessmentAggregatorActor uses). ContentService is only touched if metadata validation
  // is invoked — the viewer submit path does not call it, so no content-search network hop.
  private lazy val assessmentService = new AssessmentService(new ContentService())
  // built on the same injected CassandraOperation, so setCassandraOperation() also controls the
  // assessment persistence in tests (and keeps a single Cassandra handle in production).
  private lazy val assessmentCassandra = new CassandraService(Some(cassandraOperation))

  override def onReceive(request: Request): Unit = {
    request.getOperation match {
      case "viewStart"      => viewStart(request)
      case "viewUpdate"     => viewUpdate(request)
      case "viewEnd"        => viewEnd(request)
      case "viewRead"       => viewRead(request)
      case "viewAssess"     => viewAssess(request)
      case "assessmentRead" => assessmentRead(request)
      case _                => onReceiveUnsupportedOperation(request.getOperation)
    }
  }

  /**
   * /v1/assessment/submit (api.view.assess). Scores the attempt (reuse AssessmentService +
   * CassandraService — identical to legacy AssessmentAggregatorActor), then treats the assessment
   * like a completed content: mark ucc status=2 and run the same sync rollup as viewEnd, so the
   * assessment leaf counts toward collection completion. Score aggregates (score:cid/max_score:cid)
   * land in user_activity_agg via putAll append; the rollup's completion agg uses different keys →
   * they coexist. Request: userId, courseId?, batchId?, contentId, assessments[] (assess events).
   */
  private def viewAssess(request: Request): Unit = {
    val ctx = request.getRequestContext
    val key = viewKey(request)
    val userId = key.get("userid").asInstanceOf[String]
    val courseId = key.get("courseid").asInstanceOf[String]
    val batchId = key.get("batchid").asInstanceOf[String]
    val contentId = key.get("contentid").asInstanceOf[String]

    val eventsRaw = Option(request.get(JsonKey.ASSESSMENT_EVENTS)).orElse(Option(request.get(JsonKey.EVENTS)))
      .map(_.asInstanceOf[util.List[util.Map[String, AnyRef]]]).getOrElse(new util.ArrayList[util.Map[String, AnyRef]]())
    val events: List[AssessmentEvent] = eventsRaw.asScala.map(AssessmentParser.mapToEvent).toList

    if (events.nonEmpty) {
      val ts = Option(request.get("assessmentTs")).orElse(Option(request.get("assessmentTimestamp")))
        .map(_.asInstanceOf[Number].longValue()).getOrElse(System.currentTimeMillis())
      val attemptId = Option(request.get(JsonKey.ATTEMPT_ID)).map(_.toString).filter(StringUtils.isNotBlank)
        .getOrElse(java.util.UUID.randomUUID().toString) // no client attemptId -> a fresh attempt (avoids hashCode collisions overwriting a prior attempt)
      val unique = assessmentService.getUniqueQuestions(events)
      val metrics = assessmentService.computeScoreMetrics(unique)
      val result = AssessmentResult(attemptId, userId, courseId, batchId, contentId,
        metrics.totalScore, metrics.totalMaxScore, metrics.grandTotal, metrics.questions, System.currentTimeMillis(), ts)
      assessmentCassandra.saveAssessment(result, ctx)
      // best-score across all attempts -> user_activity_agg (reuse legacy aggregation)
      val stored = assessmentCassandra.getUserAssessments(userId, courseId, batchId, contentId, ctx)
      val agg = assessmentService.computeUserAggregates(userId, courseId, batchId, stored)
      assessmentCassandra.updateUserActivity(userId, courseId, batchId, agg, ctx)
    } else {
      logger.warn(ctx, s"viewAssess: no assessment events for userId=$userId contentId=$contentId; marking complete only", null)
    }

    // Assessment content is complete on submit (score-independent, mirrors legacy status=2) -> ucc + rollup.
    val row = new util.HashMap[String, AnyRef](key)
    row.put("status", Integer.valueOf(2))
    row.put("last_completed_time", ProjectUtil.getTimeStamp)
    row.put("last_updated_time", ProjectUtil.getTimeStamp)
    cassandraOperation.upsertRecord(consumptionDBInfo.getKeySpace, CONSUMPTION_TABLE, row.asInstanceOf[util.Map[String, AnyRef]], ctx)
    touchEnrolmentAccess(key, 2, ctx)
    triggerAggregation(request, ctx)

    val out = new Response(); out.put(contentId, JsonKey.SUCCESS); sender().tell(out, self)
  }

  /**
   * /v1/assessment/read (api.assessment.read). Best score / max score per content from
   * assessment_aggregator (reuse getUserAssessments). Request: userId, contentId[] , courseId?, batchId?.
   */
  private def assessmentRead(request: Request): Unit = {
    val ctx = request.getRequestContext
    val userId = request.get(JsonKey.USER_ID).asInstanceOf[String]
    val courseId = ViewerRequestKeys.courseId(request).orNull
    val batchId = ViewerRequestKeys.batchId(request).orNull
    val contentIds: List[String] = request.get("contentId") match {
      case l: util.List[_] => l.asScala.map(_.asInstanceOf[String]).toList
      case s: String if StringUtils.isNotBlank(s) => List(s)
      case _ => List.empty
    }
    val contents = new util.ArrayList[util.Map[String, AnyRef]]()
    contentIds.foreach { cid =>
      val stored = assessmentCassandra.getUserAssessments(userId, courseId, batchId, cid, ctx)
      if (stored.nonEmpty) {
        val best = stored.maxBy(_.totalScore)
        val m = new util.HashMap[String, AnyRef]()
        m.put("identifier", cid)
        m.put("score", best.totalScore.asInstanceOf[AnyRef])
        m.put("max_score", best.totalMaxScore.asInstanceOf[AnyRef])
        contents.add(m)
      }
    }
    val out = new Response()
    out.put(JsonKey.USER_ID, userId)
    out.put("courseId", courseId)
    out.put("batchId", batchId)
    out.put("contents", contents)
    sender().tell(out, self)
  }

  /** Raw ucc rows for a user's content(s) under a collection. context=all -> ignore batchid. */
  private def viewRead(request: Request): Unit = {
    val ctx = request.getRequestContext
    val userId = request.get(JsonKey.USER_ID).asInstanceOf[String]
    val allContexts = "all".equalsIgnoreCase(request.get("context").asInstanceOf[String])
    val filters = new util.HashMap[String, AnyRef]()
    filters.put("userid", userId)
    ViewerRequestKeys.courseId(request).foreach(c => filters.put("courseid", c))
    if (!allContexts) ViewerRequestKeys.batchId(request).foreach(c => filters.put("batchid", c))
    val contentIds = request.get("contentId") match {
      case l: util.List[_] => l.asScala.map(_.asInstanceOf[String]).asJava
      case s: String if StringUtils.isNotBlank(s) => util.Arrays.asList(s)
      case _ => null
    }
    if (contentIds != null && !contentIds.isEmpty) filters.put("contentid", contentIds)
    val response = cassandraOperation.getRecords(consumptionDBInfo.getKeySpace, CONSUMPTION_TABLE,
      filters.asInstanceOf[util.Map[String, AnyRef]], null, ctx)
    val rows = response.getResult.getOrDefault(JsonKey.RESPONSE, new util.ArrayList[util.Map[String, AnyRef]])
    val out = new Response(); out.put(JsonKey.RESPONSE, rows); sender().tell(out, self)
  }

  private def viewStart(request: Request): Unit = {
    val ctx = request.getRequestContext
    val key = viewKey(request)
    val existing = readRow(key, ctx)
    if (existing == null) {
      val row = new util.HashMap[String, AnyRef](key)
      row.put("status", Integer.valueOf(1))
      row.put("last_access_time", ProjectUtil.getTimeStamp)
      row.put("last_updated_time", ProjectUtil.getTimeStamp)
      cassandraOperation.upsertRecord(consumptionDBInfo.getKeySpace, CONSUMPTION_TABLE, row.asInstanceOf[util.Map[String, AnyRef]], ctx)
    }
    // present -> already started, no-op
    touchEnrolmentAccess(key, 1, ctx)
    sender().tell(successResponse(), self)
  }

  private def viewUpdate(request: Request): Unit = {
    val ctx = request.getRequestContext
    val key = viewKey(request)
    val existing = readRow(key, ctx)
    if (existing != null && statusOf(existing) < 2) {
      val row = new util.HashMap[String, AnyRef](key)
      // status is monotonic; an update never downgrades and never completes (that's viewEnd)
      row.put("status", Integer.valueOf(math.max(1, statusOf(existing))))
      Option(request.get("progressDetails")).foreach(pd => row.put("progressdetails", mapper.writeValueAsString(pd)))
      row.put("last_access_time", ProjectUtil.getTimeStamp)
      row.put("last_updated_time", ProjectUtil.getTimeStamp)
      cassandraOperation.upsertRecord(consumptionDBInfo.getKeySpace, CONSUMPTION_TABLE, row.asInstanceOf[util.Map[String, AnyRef]], ctx)
    }
    // absent -> update only if exists (ignore); already completed -> revisit ignored
    touchEnrolmentAccess(key, math.max(1, if (existing != null) statusOf(existing) else 1), ctx)
    sender().tell(successResponse(), self)
  }

  private def viewEnd(request: Request): Unit = {
    val ctx = request.getRequestContext
    val key = viewKey(request)
    val row = new util.HashMap[String, AnyRef](key)
    row.put("status", Integer.valueOf(2))
    Option(request.get("progressDetails")).foreach(pd => row.put("progressdetails", mapper.writeValueAsString(pd)))
    row.put("last_completed_time", ProjectUtil.getTimeStamp)
    row.put("last_updated_time", ProjectUtil.getTimeStamp)
    cassandraOperation.upsertRecord(consumptionDBInfo.getKeySpace, CONSUMPTION_TABLE, row.asInstanceOf[util.Map[String, AnyRef]], ctx)
    touchEnrolmentAccess(key, 2, ctx)
    // Async rollup: fire-and-forget tell to the aggregator; respond immediately (does not wait).
    triggerAggregation(request, ctx)
    sender().tell(successResponse(), self)
  }

  /** Fire-and-forget tell to ViewerAggregatorActor; the rollup runs async — the response does not wait. */
  private def triggerAggregation(request: Request, ctx: RequestContext): Unit = {
    val key = viewKey(request)
    val aggRequest = new Request()
    aggRequest.setOperation("aggregate")
    aggRequest.setRequestContext(ctx)
    aggRequest.put(JsonKey.USER_ID, key.get("userid"))
    aggRequest.put("courseId", key.get("courseid"))
    aggRequest.put("batchId", key.get("batchid"))
    // Async, fire-and-forget: the rollup + LP progression run in the background on the aggregator
    // (per-user serialized). The hot path does not wait for it — the change from before is ask -> tell.
    viewerAggregatorActor.tell(aggRequest, ActorRef.noSender)
  }

  /**
   * Build the ucc primary key (live column names userid, courseid, batchid, contentid).
   * Backward-compatible request keys: courseId (else legacy courseId), batchId (else legacy
   * batchId). No collection ctx -> courseid = batchid = contentId.
   */
  private def viewKey(request: Request): util.HashMap[String, AnyRef] = {
    // explicit userId (internal delegation) else requestedFor/requestedBy (from token on direct API calls)
    val userId = Option(request.get(JsonKey.USER_ID).asInstanceOf[String]).filter(StringUtils.isNotBlank)
      .orElse(Option(request.get(JsonKey.REQUESTED_FOR).asInstanceOf[String]).filter(StringUtils.isNotBlank))
      .getOrElse(request.get(JsonKey.REQUESTED_BY).asInstanceOf[String])
    val contentId = ViewerRequestKeys.contentId(request)
    val courseId = ViewerRequestKeys.courseId(request).getOrElse(contentId)
    val batchId = ViewerRequestKeys.batchId(request).getOrElse(contentId)
    val key = new util.HashMap[String, AnyRef]()
    key.put("userid", userId)
    key.put("courseid", courseId)
    key.put("batchid", batchId)
    key.put("contentid", contentId)
    key
  }

  private def readRow(key: util.HashMap[String, AnyRef], ctx: RequestContext): util.Map[String, AnyRef] = {
    val filters = new util.HashMap[String, AnyRef](key)
    val response = cassandraOperation.getRecords(consumptionDBInfo.getKeySpace, CONSUMPTION_TABLE,
      filters.asInstanceOf[util.Map[String, AnyRef]], null, ctx)
    val rows = response.getResult
      .getOrDefault(JsonKey.RESPONSE, new util.ArrayList[util.Map[String, AnyRef]])
      .asInstanceOf[util.List[util.Map[String, AnyRef]]]
    if (CollectionUtils.isNotEmpty(rows)) rows.get(0) else null
  }

  private def statusOf(row: util.Map[String, AnyRef]): Int =
    Option(row.get("status")).map(_.asInstanceOf[Number].intValue()).getOrElse(0)

  // for tests
  def setCassandraOperation(ops: org.sunbird.cassandra.CassandraOperation): ViewConsumptionActor = {
    cassandraOperation = ops
    this
  }
}
