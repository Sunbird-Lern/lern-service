package org.sunbird.viewer.actor

import org.sunbird.cassandra.CassandraOperation
import org.sunbird.enrolments.BaseEnrolmentActor
import org.sunbird.exception.ProjectCommonException
import org.sunbird.helper.ServiceFactory
import org.sunbird.keys.JsonKey
import org.sunbird.learner.util.Util
import org.sunbird.request.{Request, RequestContext}
import org.sunbird.response.{Response, ResponseCode}
import org.sunbird.viewer.competency.{CompetencyService, GapCalculator, GapRow, RoleAssignment, RoleSource}

import java.util
import scala.collection.JavaConverters._

/** Read and write APIs for the skill profile, the gap against a role, and the evidence ledger. */
class CompetencyActor extends BaseEnrolmentActor {

  private var cassandraOperation: CassandraOperation = ServiceFactory.getInstance
  private val enrolmentDBInfo = Util.dbInfoMap.get(JsonKey.LEARNER_COURSE_DB)
  private var service: CompetencyService =
    CompetencyService(cassandraOperation, enrolmentDBInfo.getKeySpace)

  override def onReceive(request: Request): Unit = {
    request.getOperation match {
      case "profileRead"     => profileRead(request)
      case "gapRead"         => gapRead(request)
      case "recommend"       => recommend(request)
      case "roleUpdate"      => roleUpdate(request)
      case "evidenceImport"  => evidenceImport(request)
      case "evidenceRevoke"  => evidenceRevoke(request)
      case "frameworkRead"   => frameworkRead(request)
      case "reproject"       => reproject(request)
      case "cacheInvalidate" => cacheInvalidate(request)
      case _                 => onReceiveUnsupportedOperation(request.getOperation)
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

  /** The learner's held skills, optionally with the supporting evidence expanded. */
  private def profileRead(request: Request): Unit = {
    val ctx = request.getRequestContext
    val uid = userId(request)
    val withEvidence = Option(request.get("evidence")).exists(v => "true".equalsIgnoreCase(v.toString))
    val entries = service.profile(uid, ctx).map { e =>
      val m = new util.HashMap[String, AnyRef]()
      m.put("skillId", e.skillId)
      m.put("frameworkId", e.frameworkId)
      m.put("sourceType", e.sourceType)
      m.put("attainedOn", new util.Date(e.attainedOn))
      if (withEvidence) m.put("evidence", service.evidenceOf(uid, e.skillId, ctx).map { ev =>
        val em = new util.HashMap[String, AnyRef]()
        em.put("evidenceId", ev.evidenceId)
        em.put("sourceType", ev.sourceType)
        em.put("sourceId", ev.sourceId)
        ev.score.foreach(v => em.put("score", java.lang.Double.valueOf(v)))
        ev.maxScore.foreach(v => em.put("maxScore", java.lang.Double.valueOf(v)))
        ev.issuerId.foreach(v => em.put("issuerId", v))
        em.put("occurredOn", new util.Date(ev.occurredOn))
        em.put("revoked", java.lang.Boolean.valueOf(ev.revoked))
        em
      }.asJava)
      m
    }.asJava
    logger.info(ctx, s"competency.api: profileRead | user=$uid held=${entries.size}")
    reply("skills" -> entries, "count" -> Integer.valueOf(entries.size))
  }

  /**
   * Gap against the learner's current role and each target role.
   *
   * Both are returned rather than one: the current role says whether the learner is doing the job
   * they hold, the target says how far the next one is, and a client showing only one of those has
   * to guess which the learner wanted.
   */
  private def gapRead(request: Request): Unit = {
    val ctx = request.getRequestContext
    val uid = userId(request)
    val assignment = service.role(uid, ctx)
    val frameworkId = str(request, "frameworkId").orElse(assignment.map(_.frameworkId)).getOrElse("")
    val explicit = str(request, "role")

    if (frameworkId.isEmpty) {
      logger.info(ctx, s"competency.api: gapRead no framework | user=$uid")
      reply("frameworkId" -> "", "current" -> null, "targets" -> new util.ArrayList[AnyRef]())
      return
    }
    val current = explicit.orElse(assignment.flatMap(_.currentRole))
    val targets = if (explicit.isDefined) Nil else assignment.map(_.targetRoles.toList.sorted).getOrElse(Nil)

    logger.info(ctx, s"competency.api: gapRead | user=$uid current=${current.getOrElse("-")} " +
      s"targets=[${targets.mkString(",")}]")
    reply(
      "frameworkId" -> frameworkId,
      "current" -> current.map(r => roleGap(uid, frameworkId, r, ctx)).orNull,
      "targets" -> targets.map(r => roleGap(uid, frameworkId, r, ctx)).asJava)
  }

  private def roleGap(uid: String, frameworkId: String, roleId: String,
                      ctx: RequestContext): util.Map[String, AnyRef] = {
    val (rows, readiness) = service.gap(uid, frameworkId, roleId, ctx)
    val m = new util.HashMap[String, AnyRef]()
    m.put("role", roleId)
    m.put("readiness", Integer.valueOf(readiness))
    m.put("required", Integer.valueOf(rows.size))
    m.put("met", Integer.valueOf(rows.count(_.status == GapCalculator.MET)))
    m.put("gap", rows.map(gapRow).asJava)
    m.put("outstanding", GapCalculator.outstanding(rows).asJava)
    m
  }

  private def gapRow(r: GapRow): util.Map[String, AnyRef] = {
    val m = new util.HashMap[String, AnyRef]()
    m.put("skillId", r.skillId)
    m.put("status", r.status)
    m
  }

  /**
   * Skills still outstanding for the role.
   *
   * Returns the gap itself rather than content ids: ranking candidate paths needs a content search,
   * which belongs in the search service, and the caller filters on `skills` with these.
   */
  private def recommend(request: Request): Unit = {
    val ctx = request.getRequestContext
    val uid = userId(request)
    val assignment = service.role(uid, ctx)
    val frameworkId = str(request, "frameworkId").orElse(assignment.map(_.frameworkId)).getOrElse("")
    val roleId = str(request, "role")
      .orElse(assignment.flatMap(_.currentRole))
      .orElse(assignment.flatMap(_.targetRoles.toList.sorted.headOption)).getOrElse("")
    if (frameworkId.isEmpty || roleId.isEmpty) {
      reply("skills" -> new util.ArrayList[AnyRef](), "role" -> "")
      return
    }
    val codes = service.outstanding(uid, frameworkId, roleId, ctx)
    logger.info(ctx, s"competency.api: recommend | user=$uid role=$roleId outstanding=${codes.size}")
    reply("skills" -> codes.asJava, "role" -> roleId, "frameworkId" -> frameworkId)
  }

  private def roleUpdate(request: Request): Unit = {
    val ctx = request.getRequestContext
    val uid = userId(request)
    val existing = service.role(uid, ctx)
    val frameworkId = str(request, "frameworkId").orElse(existing.map(_.frameworkId))
      .getOrElse(throw new ProjectCommonException(
        ResponseCode.mandatoryParamsMissing.getErrorCode,
        "Missing mandatory parameter: frameworkId",
        ResponseCode.CLIENT_ERROR.getResponseCode))
    val targets = Option(request.get("targetRoles")).collect {
      case l: util.List[_] => l.asScala.map(_.toString).filter(_.nonEmpty).toSet
    }.getOrElse(existing.map(_.targetRoles).getOrElse(Set.empty))
    // only a privileged caller may set the current role; the controller enforces that
    val current = str(request, "currentRole").orElse(existing.flatMap(_.currentRole))
    service.updateRole(RoleAssignment(uid, frameworkId, current, targets,
      str(request, "source").getOrElse(RoleSource.SELF), System.currentTimeMillis()), ctx)
    reply("status" -> "SUCCESS")
  }

  private def evidenceImport(request: Request): Unit = {
    val ctx = request.getRequestContext
    val uid = require(request, "importUserId")
    val frameworkId = require(request, "frameworkId")
    val skillId = require(request, "skillId")
    val sourceId = str(request, "sourceId").getOrElse("external")
    val occurredOn = Option(request.get("occurredOn")).collect { case n: Number => n.longValue() }
      .getOrElse(System.currentTimeMillis())
    val ok = service.importExternal(uid, frameworkId, skillId, sourceId,
      str(request, "issuerId"), str(request, "note"), occurredOn, ctx)
    if (!ok) throw new ProjectCommonException(
      ResponseCode.invalidRequestData.getErrorCode,
      s"Unresolvable framework, or $skillId is not a leaf skill of it: framework=$frameworkId",
      ResponseCode.CLIENT_ERROR.getResponseCode)
    reply("status" -> "SUCCESS")
  }

  private def evidenceRevoke(request: Request): Unit = {
    val ctx = request.getRequestContext
    service.revokeEvidence(
      require(request, "revokeUserId"), require(request, "skillId"),
      require(request, "evidenceId"), str(request, "reason").getOrElse("unspecified"), ctx)
    reply("status" -> "SUCCESS")
  }

  /** Resolved framework: tier labels, the leaf skills, and each role's required set. */
  private def frameworkRead(request: Request): Unit = {
    val ctx = request.getRequestContext
    val frameworkId = require(request, "frameworkId")
    val m = service.meta(frameworkId, ctx)
    if (m.isEmpty) throw new ProjectCommonException(
      ResponseCode.resourceNotFound.getErrorCode,
      s"Competency framework did not resolve, or declares no leaf skills: $frameworkId",
      ResponseCode.RESOURCE_NOT_FOUND.getResponseCode)
    val roles: util.Map[String, util.List[String]] =
      m.roleSkills.map { case (role, skills) => role -> skills.toList.sorted.asJava }.asJava
    reply(
      "frameworkId" -> frameworkId,
      "tierLabels" -> m.tierLabels.asJava,
      "depth" -> Integer.valueOf(m.depth),
      "leafSkills" -> m.leaves.toList.sorted.asJava,
      "leafCount" -> Integer.valueOf(m.leaves.size),
      "roles" -> roles)
  }

  /** Rebuilds a learner's profile from the ledger, re-deriving evidence first. */
  private def reproject(request: Request): Unit = {
    val ctx = request.getRequestContext
    val uid = require(request, "reprojectUserId")
    val held = service.reproject(uid, ctx)
    logger.info(ctx, s"competency.api: reproject | user=$uid held=$held")
    reply("status" -> "SUCCESS", "held" -> Integer.valueOf(held))
  }

  private def cacheInvalidate(request: Request): Unit = {
    val ctx = request.getRequestContext
    val frameworkId = str(request, "frameworkId").getOrElse("")
    service.invalidate(frameworkId, ctx)
    reply("status" -> "SUCCESS")
  }

  def configure(ops: CassandraOperation, svc: CompetencyService): CompetencyActor = {
    cassandraOperation = ops; service = svc; this
  }
}
