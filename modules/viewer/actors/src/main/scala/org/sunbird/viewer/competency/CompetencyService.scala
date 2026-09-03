package org.sunbird.viewer.competency

import org.sunbird.assessment.service.CassandraService
import org.sunbird.cassandra.CassandraOperation
import org.sunbird.logging.LoggerUtil
import org.sunbird.request.RequestContext

/**
 * One entry point for everything competency-related, so call sites stay a line long.
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

  /** node id -> its `{competency, levelIndex}` claims, for the waiver test. */
  def claimIndexes(nodeIds: List[String], m: CompetencyMeta,
                   ctx: RequestContext): Map[String, List[(String, Int)]] =
    if (m.isEmpty) Map.empty
    else frameworkUtil.claimsOf(nodeIds, ctx).map { case (node, claims) =>
      node -> claims.map(c => c.code -> m.levelIndexOf(c.levelCode))
    }

  // ---- write path -----------------------------------------------------------------------------

  /** A trackable node completed. Credits its tagged competencies and projects. */
  def onNodeCompleted(userId: String, frameworkId: String, nodeId: String, batchId: String,
                      isRoot: Boolean, completedOn: Long, ctx: RequestContext): Unit = {
    val m = meta(frameworkId, ctx)
    if (m.isEmpty) return
    val touched =
      if (isRoot) ledger.creditCollection(userId, m, nodeId, batchId, completedOn, ctx)
      else ledger.creditCourse(userId, m, nodeId, batchId, completedOn, ctx)
    if (touched.nonEmpty) projector.project(userId, touched, ctx)
  }

  /** An assessment was scored. Bands each tagged competency and projects. */
  def onAssessed(userId: String, frameworkId: String, collectionId: String, batchId: String,
                 questionSetIds: List[String], ctx: RequestContext): Unit = {
    val m = meta(frameworkId, ctx)
    if (m.isEmpty || questionSetIds.isEmpty) return
    val touched = ledger.creditAssessments(userId, m, collectionId, batchId, questionSetIds, ctx)
    if (touched.nonEmpty) projector.project(userId, touched, ctx)
  }

  def importExternal(userId: String, frameworkId: String, competencyId: String, level: String,
                     sourceId: String, issuerId: Option[String], note: Option[String],
                     occurredOn: Long, expiresOn: Option[Long], ctx: RequestContext): Boolean = {
    val m = meta(frameworkId, ctx)
    if (m.isEmpty) return false
    ledger.importExternal(userId, m, competencyId, level, sourceId, issuerId, note, occurredOn, expiresOn, ctx)
      .exists { cid => projector.project(userId, Set(cid), ctx); true }
  }

  def revokeEvidence(userId: String, competencyId: String, evidenceId: String, reason: String,
                     ctx: RequestContext): Unit = {
    ledger.revoke(userId, competencyId, evidenceId, reason, ctx)
    projector.project(userId, Set(competencyId), ctx)
  }

  // ---- read path ------------------------------------------------------------------------------

  def passbook(userId: String, ctx: RequestContext): List[PassbookEntry] =
    dao.passbookOf(userId, ctx)

  def evidenceOf(userId: String, competencyId: String, ctx: RequestContext): List[Evidence] =
    ledger.evidenceOf(userId, competencyId, ctx)

  /** Competency id to (level code, level index) for everything the learner currently holds. */
  def heldLevels(userId: String, ctx: RequestContext): Map[String, (String, Int)] =
    passbook(userId, ctx)
      .filter(e => e.status == AttainmentRules.ATTAINED || e.status == AttainmentRules.EXPIRING)
      .map(e => e.competencyId -> (e.level, e.levelIndex)).toMap

  def position(userId: String, ctx: RequestContext): Option[PositionAssignment] =
    dao.positionOf(userId, ctx)

  def updatePosition(p: PositionAssignment, ctx: RequestContext): Unit = {
    dao.upsertPosition(p, ctx)
    logger.info(ctx, s"competency.position: set | user=${p.userId} current=${p.currentPosition.getOrElse("-")} " +
      s"targets=[${p.targetPositions.mkString(",")}] source=${p.source}")
  }

  /** Gap against one position. Empty when the position declares no requirements. */
  def gap(userId: String, frameworkId: String, positionId: String, ctx: RequestContext): (List[GapRow], Int) = {
    val reqs = frameworkUtil.requirements(frameworkId, positionId, ctx)
    if (reqs.isEmpty) return (Nil, 100)
    val rows = GapCalculator.rows(reqs, heldLevels(userId, ctx))
    (rows, GapCalculator.readiness(rows))
  }

  /** Requirement set per position for the whole framework. */
  def allRequirements(frameworkId: String, ctx: RequestContext): Map[String, List[RequirementDef]] =
    frameworkUtil.allRequirements(frameworkId, ctx)

  /** Competency codes still outstanding for a position, most critical first. */
  def outstanding(userId: String, frameworkId: String, positionId: String, ctx: RequestContext): List[String] =
    GapCalculator.outstanding(gap(userId, frameworkId, positionId, ctx)._1).map(_.competencyId)

  // ---- admin ----------------------------------------------------------------------------------

  def reproject(userId: String, ctx: RequestContext): Int = projector.reproject(userId, ctx)

  def sweep(ctx: RequestContext): Int = projector.sweep(ctx)

  /** Drops the framework cache so a freshly published framework is picked up at once. */
  def invalidate(frameworkId: String, ctx: RequestContext): Unit = {
    CompetencyFrameworkUtil.invalidate(frameworkId)
    logger.info(ctx, s"competency.cache: invalidated | framework=${if (frameworkId == null || frameworkId.isEmpty) "ALL" else frameworkId}")
  }

  /** Writes the requirement projection so reporting can join on it. */
  def refreshRequirements(frameworkId: String, ctx: RequestContext): Int = {
    val all = frameworkUtil.allRequirements(frameworkId, ctx)
    all.foreach { case (pos, reqs) => reqs.foreach(r => dao.upsertRequirement(frameworkId, pos, r, ctx)) }
    all.values.map(_.size).sum
  }
}

object CompetencyService {
  def apply(cassandra: CassandraOperation, keyspace: String): CompetencyService =
    new CompetencyService(cassandra, keyspace)
}
