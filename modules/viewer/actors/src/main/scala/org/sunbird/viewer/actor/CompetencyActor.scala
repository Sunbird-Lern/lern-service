package org.sunbird.viewer.actor

import org.sunbird.cassandra.CassandraOperation
import org.sunbird.enrolments.BaseEnrolmentActor
import org.sunbird.exception.ProjectCommonException
import org.sunbird.helper.ServiceFactory
import org.sunbird.keys.JsonKey
import org.sunbird.learner.util.Util
import org.sunbird.request.{Request, RequestContext}
import org.sunbird.response.{Response, ResponseCode}
import org.sunbird.viewer.competency.{CompetencyService, CoverageRow, GapCalculator, GapRow,
  RankedCandidate, RoleAssignment, RoleDefinition, RoleDiff, RoleSource}

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
      case "coverageRead"    => coverageRead(request)
      case "roleUpdate"      => roleUpdate(request)
      case "evidenceImport"  => evidenceImport(request)
      case "evidenceRevoke"  => evidenceRevoke(request)
      case "frameworkRead"   => frameworkRead(request)
      case "roleDefRead"     => roleDefRead(request)
      case "roleUpsert"      => roleUpsert(request)
      case "roleRetire"      => roleRetire(request)
      case "roleImport"      => roleImport(request)
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
   * The outstanding skills, plus the courses and paths that close them, best first.
   *
   * A target role is preferred over the current one here, unlike gapRead: a learner asking what to
   * do next is asking about where they are going, not where they already are.
   */
  private def recommend(request: Request): Unit = {
    val ctx = request.getRequestContext
    val uid = userId(request)
    val assignment = service.role(uid, ctx)
    val frameworkId = str(request, "frameworkId").orElse(assignment.map(_.frameworkId)).getOrElse("")
    val roleId = str(request, "role")
      .orElse(assignment.flatMap(_.targetRoles.toList.sorted.headOption))
      .orElse(assignment.flatMap(_.currentRole)).getOrElse("")
    if (frameworkId.isEmpty || roleId.isEmpty) {
      reply("skills" -> new util.ArrayList[AnyRef](), "candidates" -> new util.ArrayList[AnyRef](),
        "role" -> "")
      return
    }
    val (codes, ranked) = service.recommend(uid, frameworkId, roleId, ctx)
    logger.info(ctx, s"competency.api: recommend | user=$uid role=$roleId " +
      s"outstanding=${codes.size} candidates=${ranked.size}")
    reply("skills" -> codes.asJava, "candidates" -> ranked.map(candidate).asJava,
      "role" -> roleId, "frameworkId" -> frameworkId)
  }

  private def candidate(r: RankedCandidate): util.Map[String, AnyRef] = {
    val m = new util.HashMap[String, AnyRef]()
    m.put("identifier", r.candidate.id)
    m.put("name", r.candidate.name)
    m.put("primaryCategory", r.candidate.primaryCategory)
    m.put("skills", r.candidate.skills.toList.sorted.asJava)
    m.put("gapCovered", Integer.valueOf(r.gapCovered))
    m.put("alreadyHeld", Integer.valueOf(r.alreadyHeld))
    m.put("totalSkills", Integer.valueOf(r.totalSkills))
    m
  }

  /**
   * Coverage of a programme against its target role. Authoring-time, not learner-facing.
   *
   * Reports and never blocks, so a path with an uncovered skill still publishes; the author simply
   * learns which one before any learner enrols.
   */
  private def coverageRead(request: Request): Unit = {
    val ctx = request.getRequestContext
    val collectionId = require(request, "collectionId")
    val report = service.coverage(collectionId, str(request, "role"), ctx)
    val notCovered = report.rows.filter(_.status == GapCalculator.NOT_COVERED).map(_.skillId)
    reply(
      "collectionId" -> report.collectionId,
      "frameworkId" -> report.frameworkId,
      "role" -> report.roleId,
      "courses" -> Integer.valueOf(report.courses),
      "required" -> Integer.valueOf(report.rows.size),
      "covered" -> Integer.valueOf(report.rows.size - notCovered.size),
      "coverage" -> report.rows.map(coverageRow).asJava,
      "notCovered" -> notCovered.asJava,
      "taught" -> report.taught.toList.sorted.asJava,
      "unassessed" -> report.unassessed.asJava)
  }

  private def coverageRow(r: CoverageRow): util.Map[String, AnyRef] = {
    val m = new util.HashMap[String, AnyRef]()
    m.put("skillId", r.skillId)
    m.put("status", r.status)
    m.put("taughtBy", r.taughtBy.asJava)
    m
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

  // ---- role authoring -------------------------------------------------------------------------
  //
  // The role -> skill map lives in `role_skill`, not in the framework, so lern owns these writes.
  // They are ordinary authenticated routes, deliberately NOT under /private: any path containing
  // "private" skips token validation in LernServiceRequestInterceptor, and these requirements are
  // the input to every learner's readiness. Restricting them to admins is a Kong concern, which is
  // where this platform does authorisation.

  private def strings(request: Request, key: String): Set[String] =
    Option(request.get(key)).collect {
      case l: util.List[_] => l.asScala.map(_.toString.trim).filter(_.nonEmpty).toSet
    }.getOrElse(Set.empty)

  private def roleDef(m: util.Map[String, AnyRef]): RoleDefinition = {
    val code = Option(m.get("roleId")).map(_.toString.trim).filter(_.nonEmpty).getOrElse(
      throw new ProjectCommonException(
        ResponseCode.mandatoryParamsMissing.getErrorCode,
        "Every entry in `roles` needs a roleId",
        ResponseCode.CLIENT_ERROR.getResponseCode))
    val skills = Option(m.get("skills")).collect {
      case l: util.List[_] => l.asScala.map(_.toString.trim).filter(_.nonEmpty).toSet
    }.getOrElse(Set.empty)
    RoleDefinition(code, Option(m.get("name")).map(_.toString).getOrElse(code), skills)
  }

  private def diffRow(d: RoleDiff): util.Map[String, AnyRef] = {
    val m = new util.HashMap[String, AnyRef]()
    m.put("roleId", d.roleId)
    m.put("added", d.added.toList.sorted.asJava)
    m.put("removed", d.removed.toList.sorted.asJava)
    m.put("unchanged", d.unchanged.toList.sorted.asJava)
    m.put("rejected", d.rejected.toList.sorted.asJava)
    m.put("changed", java.lang.Boolean.valueOf(d.changed))
    if (d.retired) m.put("retired", java.lang.Boolean.TRUE)
    m
  }

  private def roleRow(r: RoleDefinition): util.Map[String, AnyRef] = {
    val m = new util.HashMap[String, AnyRef]()
    m.put("roleId", r.roleId)
    m.put("name", r.name)
    m.put("skills", r.skills.toList.sorted.asJava)
    m.put("skillCount", Integer.valueOf(r.skills.size))
    m.put("status", r.status)
    m.put("version", Integer.valueOf(r.version))
    m
  }

  /** Roles as authored, RETIRED included - distinct from frameworkRead, which serves learners. */
  private def roleDefRead(request: Request): Unit = {
    val ctx = request.getRequestContext
    val frameworkId = require(request, "frameworkId")
    val roles = service.roleRead(frameworkId, str(request, "roleId"), ctx)
    reply("frameworkId" -> frameworkId,
      "roles" -> roles.map(roleRow).asJava,
      "count" -> Integer.valueOf(roles.size))
  }

  /** Replaces a role's requirement set. `skills` is the FULL set; anything absent is removed. */
  private def roleUpsert(request: Request): Unit = {
    val ctx = request.getRequestContext
    val frameworkId = require(request, "frameworkId")
    val roleId = require(request, "roleId")
    val d = service.roleUpsert(frameworkId,
      RoleDefinition(roleId, str(request, "name").getOrElse(roleId), strings(request, "skills")), ctx)
    logger.info(ctx, s"competency.role: upsert | framework=$frameworkId role=$roleId " +
      s"added=${d.added.size} removed=${d.removed.size} rejected=${d.rejected.size}")
    reply("frameworkId" -> frameworkId, "diff" -> diffRow(d))
  }

  private def roleRetire(request: Request): Unit = {
    val ctx = request.getRequestContext
    val frameworkId = require(request, "frameworkId")
    val roleId = require(request, "roleId")
    val d = service.roleRetire(frameworkId, roleId, ctx)
    if (!d.retired) throw new ProjectCommonException(
      ResponseCode.resourceNotFound.getErrorCode,
      s"No such role in framework $frameworkId: $roleId",
      ResponseCode.RESOURCE_NOT_FOUND.getResponseCode)
    logger.info(ctx, s"competency.role: retired | framework=$frameworkId role=$roleId")
    reply("frameworkId" -> frameworkId, "diff" -> diffRow(d))
  }

  /** The whole authoring matrix. `dryRun: true` reports the diff without writing. */
  private def roleImport(request: Request): Unit = {
    val ctx = request.getRequestContext
    val frameworkId = require(request, "frameworkId")
    val rows = Option(request.get("roles")).collect {
      case l: util.List[_] => l.asScala.toList.collect {
        case m: util.Map[_, _] => roleDef(m.asInstanceOf[util.Map[String, AnyRef]])
      }
    }.getOrElse(Nil)
    if (rows.isEmpty) throw new ProjectCommonException(
      ResponseCode.mandatoryParamsMissing.getErrorCode,
      "Missing mandatory parameter: roles",
      ResponseCode.CLIENT_ERROR.getResponseCode)
    val dryRun = Option(request.get("dryRun")).exists(v => v.toString.equalsIgnoreCase("true"))
    val diffs = service.roleImport(frameworkId, rows, dryRun, ctx)
    logger.info(ctx, s"competency.role: import | framework=$frameworkId roles=${rows.size} " +
      s"dryRun=$dryRun changed=${diffs.count(_.changed)}")
    reply("frameworkId" -> frameworkId,
      "dryRun" -> java.lang.Boolean.valueOf(dryRun),
      "roles" -> diffs.map(diffRow).asJava,
      "changed" -> Integer.valueOf(diffs.count(_.changed)))
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
