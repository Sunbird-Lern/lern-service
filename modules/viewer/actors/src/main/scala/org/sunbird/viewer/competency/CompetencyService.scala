package org.sunbird.viewer.competency

import org.sunbird.assessment.service.CassandraService
import org.sunbird.cassandra.CassandraOperation
import org.sunbird.logging.LoggerUtil
import org.sunbird.request.RequestContext

/**
 * One entry point for everything skill-related, so call sites stay a line long.
 * Composes the framework resolver, the ledger and the projector.
 */
class CompetencyService(cassandra: CassandraOperation, keyspace: String) {

  private val logger = new LoggerUtil(classOf[CompetencyService])

  private[competency] val dao = new CompetencyDao(cassandra, keyspace)
  private[competency] val frameworkUtil = CompetencyFrameworkUtil()
  private[competency] val ledger =
    new CompetencyLedger(dao, frameworkUtil, new CassandraService(Some(cassandra)))
  private[competency] val projector = new CompetencyProjector(dao, ledger, frameworkUtil)

  /** The framework a collection declares, if any. */
  def frameworkOf(collectionId: String, ctx: RequestContext): Option[String] =
    frameworkUtil.frameworkOf(collectionId, ctx)

  def meta(frameworkId: String, ctx: RequestContext): CompetencyMeta =
    frameworkUtil.meta(frameworkId, ctx)

  /** node id -> the leaf skills it teaches, for the waiver test. */
  def claimsOf(nodeIds: List[String], m: CompetencyMeta,
               ctx: RequestContext): Map[String, List[String]] =
    if (m.isEmpty) Map.empty else frameworkUtil.leafClaimsOf(nodeIds, m, ctx)

  // ---- write path -----------------------------------------------------------------------------

  /** A trackable node completed. Records the skills it teaches and projects. */
  def onNodeCompleted(userId: String, frameworkId: String, nodeId: String, batchId: String,
                      isRoot: Boolean, completedOn: Long, ctx: RequestContext): Unit = {
    val m = meta(frameworkId, ctx)
    if (m.isEmpty) return
    val touched =
      if (isRoot) ledger.creditCollection(userId, m, nodeId, batchId, completedOn, ctx)
      else ledger.creditCourse(userId, m, nodeId, batchId, completedOn, ctx)
    if (touched.nonEmpty) projector.project(userId, touched, ctx)
  }

  /** An assessment was scored. Records every skill answered at full marks and projects. */
  def onAssessed(userId: String, frameworkId: String, collectionId: String, batchId: String,
                 questionSetIds: List[String], ctx: RequestContext): Unit = {
    val m = meta(frameworkId, ctx)
    if (m.isEmpty || questionSetIds.isEmpty) return
    val touched = ledger.creditAssessments(userId, m, collectionId, batchId, questionSetIds, ctx)
    if (touched.nonEmpty) projector.project(userId, touched, ctx)
  }

  def importExternal(userId: String, frameworkId: String, skillId: String, sourceId: String,
                     issuerId: Option[String], note: Option[String], occurredOn: Long,
                     ctx: RequestContext): Boolean = {
    val m = meta(frameworkId, ctx)
    if (m.isEmpty) return false
    ledger.importExternal(userId, m, skillId, sourceId, issuerId, note, occurredOn, ctx)
      .exists { sid => projector.project(userId, Set(sid), ctx); true }
  }

  def revokeEvidence(userId: String, skillId: String, evidenceId: String, reason: String,
                     ctx: RequestContext): Unit = {
    ledger.revoke(userId, skillId, evidenceId, reason, ctx)
    projector.project(userId, Set(skillId), ctx)
  }

  // ---- read path ------------------------------------------------------------------------------

  def profile(userId: String, ctx: RequestContext): List[SkillEntry] =
    dao.profileOf(userId, ctx)

  def evidenceOf(userId: String, skillId: String, ctx: RequestContext): List[Evidence] =
    ledger.evidenceOf(userId, skillId, ctx)

  /** Everything the learner currently holds. The waiver test and the gap both read this. */
  def heldSkills(userId: String, ctx: RequestContext): Set[String] =
    profile(userId, ctx).map(_.skillId).toSet

  def role(userId: String, ctx: RequestContext): Option[RoleAssignment] =
    dao.roleOf(userId, ctx)

  def updateRole(a: RoleAssignment, ctx: RequestContext): Unit = {
    dao.upsertRole(a, ctx)
    logger.info(ctx, s"competency.role: set | user=${a.userId} current=${a.currentRole.getOrElse("-")} " +
      s"targets=[${a.targetRoles.mkString(",")}] source=${a.source}")
  }

  /** Gap against one role. Empty when the role requires nothing. */
  def gap(userId: String, frameworkId: String, roleId: String,
          ctx: RequestContext): (List[GapRow], Int) = {
    val required = frameworkUtil.requirements(frameworkId, roleId, ctx)
    if (required.isEmpty) return (Nil, 100)
    val rows = GapCalculator.rows(required, heldSkills(userId, ctx))
    (rows, GapCalculator.readiness(rows))
  }

  /** Required skill set per role for the whole framework. */
  def allRequirements(frameworkId: String, ctx: RequestContext): Map[String, Set[String]] =
    frameworkUtil.allRequirements(frameworkId, ctx)

  /** Skills still outstanding for a role. */
  def outstanding(userId: String, frameworkId: String, roleId: String,
                  ctx: RequestContext): List[String] =
    GapCalculator.outstanding(gap(userId, frameworkId, roleId, ctx)._1)

  // ---- admin ----------------------------------------------------------------------------------

  def reproject(userId: String, ctx: RequestContext): Int = projector.reproject(userId, ctx)

  /** Drops the framework cache so a freshly published framework is picked up at once. */
  def invalidate(frameworkId: String, ctx: RequestContext): Unit = {
    CompetencyFrameworkUtil.invalidate(frameworkId)
    logger.info(ctx, s"competency.cache: invalidated | framework=" +
      s"${if (frameworkId == null || frameworkId.isEmpty) "ALL" else frameworkId}")
  }
}

object CompetencyService {
  def apply(cassandra: CassandraOperation, keyspace: String): CompetencyService =
    new CompetencyService(cassandra, keyspace)
}
