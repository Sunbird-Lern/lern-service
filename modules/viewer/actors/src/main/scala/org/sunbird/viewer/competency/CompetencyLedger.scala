package org.sunbird.viewer.competency

import org.sunbird.assessment.service.CassandraService
import org.sunbird.logging.LoggerUtil
import org.sunbird.request.RequestContext

/**
 * Appends facts. Never decides what is held — that is the projector's job.
 *
 * Idempotent per source: `append` skips a row whose (sourceType, sourceId, batchId) the skill
 * already has live, for every source but EXTERNAL. Each write goes through a `LedgerSnapshot` of
 * the learner's evidence, which the duplicate check reads and every insert and revocation updates.
 * Each method returns the skill ids it touched, so the caller can settle badges.
 *
 * A row is appended only for a skill the learner earned. Nothing is written for a skill that was
 * attempted and missed, because the profile reads held as "a live row exists".
 */
class CompetencyLedger(dao: CompetencyDao,
                       frameworkUtil: CompetencyFrameworkUtil,
                       assessmentService: CassandraService) {

  private val logger = new LoggerUtil(classOf[CompetencyLedger])

  /** Completing a course records every leaf skill it teaches. */
  def creditCourse(snap: LedgerSnapshot, meta: CompetencyMeta, courseId: String, batchId: String,
                   completedOn: Long, ctx: RequestContext): Set[String] =
    creditCompletion(snap, meta, courseId, batchId, SourceType.COURSE, completedOn, ctx)

  /** Completing a Learning Path records the skills the programme itself declares. */
  def creditCollection(snap: LedgerSnapshot, meta: CompetencyMeta, rootId: String, batchId: String,
                       completedOn: Long, ctx: RequestContext): Set[String] =
    creditCompletion(snap, meta, rootId, batchId, SourceType.LEARNING_PATH, completedOn, ctx)

  private def creditCompletion(snap: LedgerSnapshot, meta: CompetencyMeta, nodeId: String, batchId: String,
                               sourceType: String, completedOn: Long, ctx: RequestContext): Set[String] = {
    if (meta.isEmpty) return Set.empty
    val skills = frameworkUtil.leafClaimsOf(List(nodeId), meta, ctx).getOrElse(nodeId, Nil)
    skills.map { code =>
      append(snap, Evidence(
        userId = snap.userId, skillId = code,
        evidenceId = AttainmentRules.evidenceId(completedOn, sourceType, nodeId, batchId),
        frameworkId = meta.frameworkId,
        sourceType = sourceType, sourceId = nodeId, batchId = batchId,
        score = None, maxScore = None,
        issuerId = None, note = None, occurredOn = completedOn), ctx)
      code
    }.toSet
  }

  /**
   * Records every skill the learner answered at full marks.
   *
   * Attempts are unioned rather than reduced to a best one. Under binary attainment "best" is not
   * a meaningful unit: a skill answered perfectly in the first attempt would go uncredited if a
   * later attempt scored higher overall but got that skill wrong. Attempts are walked oldest first,
   * so the row kept for a (skill, question set) is the attempt that first earned it; later passing
   * attempts of the same question set are duplicates.
   */
  def creditAssessments(snap: LedgerSnapshot, meta: CompetencyMeta, collectionId: String, batchId: String,
                        questionSetIds: List[String], ctx: RequestContext): Set[String] = {
    if (meta.isEmpty || questionSetIds.isEmpty) return Set.empty
    questionSetIds.flatMap { qs =>
      val attempts = assessmentService.getUserAssessments(snap.userId, collectionId, batchId, qs, ctx)
        .toList.sortBy(_.lastAttemptedOn)
      attempts.flatMap { attempt =>
        val answered = attempt.questions.toList
        if (answered.isEmpty) Nil
        else {
          val claims = frameworkUtil.leafClaimsOf(answered.map(_.questionId), meta, ctx)
          val bySkill = answered
            .flatMap(q => claims.getOrElse(q.questionId, Nil).map(code => code -> q))
            .groupBy(_._1)
            .map { case (code, pairs) => code -> pairs.map(_._2) }
          val occurredOn =
            if (attempt.lastAttemptedOn > 0) attempt.lastAttemptedOn else System.currentTimeMillis()
          val earned = AttainmentRules.earnedIn(
            bySkill.map { case (code, qsForSkill) => code -> qsForSkill.map(q => (q.score, q.maxScore)) })
          earned.toList.map { code =>
            val qsForSkill = bySkill(code)
            append(snap, Evidence(
              userId = snap.userId, skillId = code,
              evidenceId = AttainmentRules.evidenceId(occurredOn, SourceType.ASSESSMENT, qs, batchId),
              frameworkId = meta.frameworkId,
              sourceType = SourceType.ASSESSMENT, sourceId = qs, batchId = batchId,
              score = Some(qsForSkill.map(_.score).sum),
              maxScore = Some(qsForSkill.map(_.maxScore).sum),
              issuerId = None, note = None, occurredOn = occurredOn), ctx)
            code
          }
        }
      }
    }.toSet
  }

  /** Registers a credential earned outside the platform. */
  def importExternal(snap: LedgerSnapshot, meta: CompetencyMeta, skillId: String, sourceId: String,
                     issuerId: Option[String], note: Option[String], occurredOn: Long,
                     ctx: RequestContext): Option[String] = {
    if (meta.isEmpty) return None
    if (!meta.isLeaf(skillId)) {
      logger.info(ctx, s"competency.ledger: import rejected, $skillId is not a leaf of ${meta.frameworkId}")
      return None
    }
    append(snap, Evidence(
      userId = snap.userId, skillId = skillId,
      evidenceId = AttainmentRules.evidenceId(occurredOn, SourceType.EXTERNAL, sourceId, ""),
      frameworkId = meta.frameworkId,
      sourceType = SourceType.EXTERNAL, sourceId = sourceId, batchId = "",
      score = None, maxScore = None,
      issuerId = issuerId, note = note, occurredOn = occurredOn), ctx)
    Some(skillId)
  }

  def revoke(snap: LedgerSnapshot, skillId: String, evidenceId: String, reason: String,
             ctx: RequestContext): Unit = {
    snap.load() // before the write, so the projector's `before` still holds this row live
    dao.revokeEvidence(snap.userId, skillId, evidenceId, reason, ctx)
    snap.revoked(skillId, evidenceId, reason)
    logger.info(ctx, s"competency.ledger: revoked | user=${snap.userId} skill=$skillId evidence=$evidenceId")
  }

  /**
   * Appends evidence, unless this exact source already evidenced the skill.
   *
   * IDEMPOTENT PER SOURCE, not per call. `evidenceId` is "<occurredOn>:<digest of
   * sourceType|sourceId|batchId>", so the same source credited twice at different moments yields
   * two different ids and two rows - the ledger would grow every time a rollup re-runs or a learner
   * passes the same question set again. The source triple is the real identity; see
   * `AttainmentRules.isDuplicate`.
   */
  private def append(snap: LedgerSnapshot, e: Evidence, ctx: RequestContext): Unit = {
    val already = AttainmentRules.isDuplicate(
      snap.ofSkill(e.skillId).map(x => (x.sourceType, x.sourceId, x.batchId, x.revoked)),
      e.sourceType, e.sourceId, e.batchId)
    if (already) {
      logger.info(ctx, s"competency.ledger: append skip(duplicate source) | user=${e.userId} " +
        s"skill=${e.skillId} source=${e.sourceType}:${e.sourceId}")
      return
    }
    dao.insertEvidence(e, ctx)
    snap.added(e)
    logger.info(ctx, s"competency.ledger: append | user=${e.userId} skill=${e.skillId} " +
      s"source=${e.sourceType}:${e.sourceId}")
  }
}
