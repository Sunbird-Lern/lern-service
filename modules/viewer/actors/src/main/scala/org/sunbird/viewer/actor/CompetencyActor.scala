package org.sunbird.viewer.actor

import org.apache.commons.lang3.StringUtils
import org.sunbird.cassandra.CassandraOperation
import org.sunbird.enrolments.BaseEnrolmentActor
import org.sunbird.exception.ProjectCommonException
import org.sunbird.helper.ServiceFactory
import org.sunbird.keys.JsonKey
import org.sunbird.learner.util.Util
import org.sunbird.request.Request
import org.sunbird.response.{Response, ResponseCode}
import org.sunbird.viewer.competency.{CompetencyService, GapCalculator, PositionAssignment}

import java.util
import scala.collection.JavaConverters._

/** Read and write APIs for the competency passbook, gap and evidence ledger. */
class CompetencyActor extends BaseEnrolmentActor {

  private var cassandraOperation: CassandraOperation = ServiceFactory.getInstance
  private val enrolmentDBInfo = Util.dbInfoMap.get(JsonKey.LEARNER_COURSE_DB)
  private var service: CompetencyService =
    CompetencyService(cassandraOperation, enrolmentDBInfo.getKeySpace)

  override def onReceive(request: Request): Unit = {
    request.getOperation match {
      case "passbookRead"       => passbookRead(request)
      case "gapRead"            => gapRead(request)
      case "recommend"          => recommend(request)
      case "positionUpdate"     => positionUpdate(request)
      case "evidenceImport"     => evidenceImport(request)
      case "evidenceRevoke"     => evidenceRevoke(request)
      case "frameworkRead"      => frameworkRead(request)
      case "reproject"          => reproject(request)
      case "cacheInvalidate"    => cacheInvalidate(request)
      case "expirySweep"        => expirySweep(request)
      case _                    => onReceiveUnsupportedOperation(request.getOperation)
    }
  }

  private def userId(request: Request): String = request.get(JsonKey.USER_ID).asInstanceOf[String]

  private def str(request: Request, key: String): Option[String] =
    Option(request.get(key)).map(_.toString).map(_.trim).filter(_.nonEmpty)

  private def require(request: Request, key: String): String =
    str(request, key).getOrElse(throw new ProjectCommonException(
      ResponseCode.mandatoryParamsMissing.getErrorCode,
      s"Missing mandatory parameter: $key",
      ResponseCode.CLIENT_ERROR.getResponseCode))

  private def reply(payload: (String, AnyRef)*): Unit = {
    val response = new Response
    payload.foreach { case (k, v) => response.put(k, v) }
    sender().tell(response, self)
  }

  /** The learner's passbook, optionally with the supporting evidence expanded. */
  private def passbookRead(request: Request): Unit = {
    val ctx = request.getRequestContext
    val uid = userId(request)
    val withEvidence = Option(request.get("evidence")).exists(v => "true".equalsIgnoreCase(v.toString))
    val entries = service.passbook(uid, ctx).map { e =>
      val m = new util.HashMap[String, AnyRef]()
      m.put("competencyId", e.competencyId)
      m.put("frameworkId", e.frameworkId)
      m.put("level", e.level)
      m.put("levelIndex", Integer.valueOf(e.levelIndex))
      m.put("status", e.status)
      m.put("sourceType", e.sourceType)
      m.put("attainedOn", new util.Date(e.attainedOn))
      e.expiresOn.foreach(v => m.put("expiresOn", new util.Date(v)))
      if (withEvidence) m.put("evidence", service.evidenceOf(uid, e.competencyId, ctx).map { ev =>
        val em = new util.HashMap[String, AnyRef]()
        em.put("evidenceId", ev.evidenceId)
        em.put("level", ev.level)
        em.put("sourceType", ev.sourceType)
        em.put("sourceId", ev.sourceId)
        ev.score.foreach(v => em.put("score", java.lang.Double.valueOf(v)))
        ev.maxScore.foreach(v => em.put("maxScore", java.lang.Double.valueOf(v)))
        em.put("occurredOn", new util.Date(ev.occurredOn))
        em.put("revoked", java.lang.Boolean.valueOf(ev.revoked))
        em
      }.asJava)
      m
    }.asJava
    logger.info(ctx, s"competency.api: passbookRead | user=$uid n=${entries.size}")
    reply("competencies" -> entries, "count" -> Integer.valueOf(entries.size))
  }

  /** Gap and readiness against the learner's current position, or an explicitly named one. */
  private def gapRead(request: Request): Unit = {
    val ctx = request.getRequestContext
    val uid = userId(request)
    val assignment = service.position(uid, ctx)
    val frameworkId = str(request, "frameworkId").orElse(assignment.map(_.frameworkId)).getOrElse("")
    val positionId = str(request, "position").orElse(assignment.flatMap(_.currentPosition)).getOrElse("")
    if (frameworkId.isEmpty || positionId.isEmpty) {
      logger.info(ctx, s"competency.api: gapRead no position | user=$uid")
      reply("gap" -> new util.ArrayList[AnyRef](), "readiness" -> Integer.valueOf(0),
        "position" -> "", "frameworkId" -> frameworkId)
      return
    }
    val (rows, readiness) = service.gap(uid, frameworkId, positionId, ctx)
    logger.info(ctx, s"competency.api: gapRead | user=$uid position=$positionId readiness=$readiness")
    reply(
      "gap" -> rows.map(gapRow).asJava,
      "readiness" -> Integer.valueOf(readiness),
      "position" -> positionId,
      "frameworkId" -> frameworkId,
      "mandatoryOutstanding" -> Integer.valueOf(rows.count(r => GapCalculator.isMandatory(r) && r.status != GapCalculator.MET)))
  }

  private def gapRow(r: org.sunbird.viewer.competency.GapRow): util.Map[String, AnyRef] = {
    val m = new util.HashMap[String, AnyRef]()
    m.put("competencyId", r.competencyId)
    m.put("requiredLevel", r.requiredLevel)
    m.put("requiredLevelIndex", Integer.valueOf(r.requiredLevelIndex))
    m.put("heldLevel", r.heldLevel)
    m.put("heldLevelIndex", Integer.valueOf(r.heldLevelIndex))
    m.put("criticality", r.criticality)
    m.put("status", r.status)
    m
  }

  /**
   * Competencies still outstanding for the position, most critical first.
   *
   * Returns the gap itself rather than content ids: ranking candidate paths needs a content search,
   * which belongs in the search service, and the caller filters on `competencyCodes` with these.
   */
  private def recommend(request: Request): Unit = {
    val ctx = request.getRequestContext
    val uid = userId(request)
    val assignment = service.position(uid, ctx)
    val frameworkId = str(request, "frameworkId").orElse(assignment.map(_.frameworkId)).getOrElse("")
    val positionId = str(request, "position")
      .orElse(assignment.flatMap(_.currentPosition))
      .orElse(assignment.flatMap(_.targetPositions.headOption)).getOrElse("")
    if (frameworkId.isEmpty || positionId.isEmpty) {
      reply("competencyCodes" -> new util.ArrayList[AnyRef](), "position" -> "")
      return
    }
    val codes = service.outstanding(uid, frameworkId, positionId, ctx)
    logger.info(ctx, s"competency.api: recommend | user=$uid position=$positionId outstanding=${codes.size}")
    reply("competencyCodes" -> codes.asJava, "position" -> positionId, "frameworkId" -> frameworkId)
  }

  private def positionUpdate(request: Request): Unit = {
    val ctx = request.getRequestContext
    val uid = userId(request)
    val existing = service.position(uid, ctx)
    val frameworkId = str(request, "frameworkId").orElse(existing.map(_.frameworkId))
      .getOrElse(throw new ProjectCommonException(
        ResponseCode.mandatoryParamsMissing.getErrorCode,
        "Missing mandatory parameter: frameworkId",
        ResponseCode.CLIENT_ERROR.getResponseCode))
    val targets = Option(request.get("targetPositions")).collect {
      case l: util.List[_] => l.asScala.map(_.toString).toSet
    }.getOrElse(existing.map(_.targetPositions).getOrElse(Set.empty))
    // only a privileged caller may set the current position; the controller enforces that
    val current = str(request, "currentPosition").orElse(existing.flatMap(_.currentPosition))
    service.updatePosition(PositionAssignment(uid, frameworkId, current, targets,
      str(request, "source").getOrElse("SELF"), System.currentTimeMillis()), ctx)
    reply("status" -> "SUCCESS")
  }

  private def evidenceImport(request: Request): Unit = {
    val ctx = request.getRequestContext
    val uid = require(request, "importUserId")
    val frameworkId = require(request, "frameworkId")
    val competencyId = require(request, "competencyId")
    val level = require(request, "level")
    val sourceId = str(request, "sourceId").getOrElse("external")
    val occurredOn = Option(request.get("occurredOn")).collect { case n: Number => n.longValue() }
      .getOrElse(System.currentTimeMillis())
    val expiresOn = Option(request.get("expiresOn")).collect { case n: Number => n.longValue() }
    val ok = service.importExternal(uid, frameworkId, competencyId, level, sourceId,
      str(request, "issuerId"), str(request, "note"), occurredOn, expiresOn, ctx)
    if (!ok) throw new ProjectCommonException(
      ResponseCode.invalidRequestData.getErrorCode,
      s"Unresolvable competency framework or level: framework=$frameworkId level=$level",
      ResponseCode.CLIENT_ERROR.getResponseCode)
    reply("status" -> "SUCCESS")
  }

  private def evidenceRevoke(request: Request): Unit = {
    val ctx = request.getRequestContext
    service.revokeEvidence(
      require(request, "revokeUserId"), require(request, "competencyId"),
      require(request, "evidenceId"), str(request, "reason").getOrElse("unspecified"), ctx)
    reply("status" -> "SUCCESS")
  }

  /** Resolved framework: the scale, and the requirement set per position. */
  private def frameworkRead(request: Request): Unit = {
    val ctx = request.getRequestContext
    val frameworkId = require(request, "frameworkId")
    val m = service.meta(frameworkId, ctx)
    if (m.isEmpty) throw new ProjectCommonException(
      ResponseCode.resourceNotFound.getErrorCode,
      s"Competency framework did not resolve: $frameworkId",
      ResponseCode.RESOURCE_NOT_FOUND.getResponseCode)
    val levels = m.levels.map { l =>
      val lm = new util.HashMap[String, AnyRef]()
      lm.put("code", l.code); lm.put("index", Integer.valueOf(l.index))
      lm.put("cutScore", java.lang.Double.valueOf(l.cutScore))
      lm.put("minEvidenceCount", Integer.valueOf(l.minEvidenceCount))
      l.validityMonths.foreach(v => lm.put("validityMonths", Integer.valueOf(v)))
      lm
    }.asJava
    val requirements: util.Map[String, util.List[util.Map[String, AnyRef]]] =
      service.allRequirements(frameworkId, ctx).map { case (pos, reqs) =>
      pos -> reqs.map { r =>
        val rm: util.Map[String, AnyRef] = new util.HashMap[String, AnyRef]()
        rm.put("competencyId", r.competencyId)
        rm.put("requiredLevel", r.requiredLevel)
        rm.put("requiredLevelIndex", Integer.valueOf(r.requiredLevelIndex))
        rm.put("criticality", r.criticality)
        rm
      }.asJava
    }.asJava
    reply("frameworkId" -> frameworkId, "levels" -> levels, "requirements" -> requirements,
      "defaultRequiredLevel" -> m.defaultRequiredLevel,
      "maxCompletionDerivedIndex" -> Integer.valueOf(m.maxCompletionDerivedIndex))
  }

  /** Rebuilds a learner's passbook from the ledger, re-deriving evidence first. */
  private def reproject(request: Request): Unit = {
    val ctx = request.getRequestContext
    val uid = require(request, "reprojectUserId")
    val written = service.reproject(uid, ctx)
    logger.info(ctx, s"competency.api: reproject | user=$uid entries=$written")
    reply("status" -> "SUCCESS", "entries" -> Integer.valueOf(written))
  }

  private def cacheInvalidate(request: Request): Unit = {
    val ctx = request.getRequestContext
    val frameworkId = str(request, "frameworkId").getOrElse("")
    service.invalidate(frameworkId, ctx)
    val refreshed = if (frameworkId.nonEmpty) service.refreshRequirements(frameworkId, ctx) else 0
    reply("status" -> "SUCCESS", "requirementsRefreshed" -> Integer.valueOf(refreshed))
  }

  private def expirySweep(request: Request): Unit = {
    val ctx = request.getRequestContext
    val touched = service.sweep(ctx)
    logger.info(ctx, s"competency.api: expirySweep | touched=$touched")
    reply("status" -> "SUCCESS", "touched" -> Integer.valueOf(touched))
  }

  def configure(ops: CassandraOperation, svc: CompetencyService): CompetencyActor = {
    cassandraOperation = ops; service = svc; this
  }
}
