package org.sunbird.viewer.competency

import org.sunbird.assessment.service.CassandraService
import org.sunbird.logging.LoggerUtil
import org.sunbird.request.RequestContext

/**
 * Appends facts. Never decides what is held — that is the projector's job.
 *
 * Every write is idempotent: the evidence id is derived from (occurredOn, sourceType, sourceId,
 * batchId), so replaying a completion or an aggregate rewrites the same row rather than appending
 * a duplicate. Each method returns the skill ids it touched, for the caller to project.
 *
 * A row is appended only for a skill the learner earned. Nothing is written for a skill that was
 * attempted and missed, because the profile reads held as "a live row exists".
 */
class CompetencyLedger(dao: CompetencyDao,
                       frameworkUtil: CompetencyFrameworkUtil,
                       assessmentService: CassandraService) {

  private val logger = new LoggerUtil(classOf[CompetencyLedger])

  /** Completing a course records every leaf skill it teaches. */
  def creditCourse(userId: String, meta: CompetencyMeta, courseId: String, batchId: String,
                   completedOn: Long, ctx: RequestContext): Set[String] =
    creditCompletion(userId, meta, courseId, batchId, SourceType.COURSE, completedOn, ctx)

  /** Completing a Learning Path records the skills the programme itself declares. */
  def creditCollection(userId: String, meta: CompetencyMeta, rootId: String, batchId: String,
                       completedOn: Long, ctx: RequestContext): Set[String] =
    creditCompletion(userId, meta, rootId, batchId, SourceType.LEARNING_PATH, completedOn, ctx)

  private def creditCompletion(userId: String, meta: CompetencyMeta, nodeId: String, batchId: String,
                               sourceType: String, completedOn: Long, ctx: RequestContext): Set[String] = {
    if (meta.isEmpty) return Set.empty
    val skills = frameworkUtil.leafClaimsOf(List(nodeId), meta, ctx).getOrElse(nodeId, Nil)
    skills.map { code =>
      append(Evidence(
        userId = userId, skillId = code,
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
   * later attempt scored higher overall but got that skill wrong. Each qualifying attempt appends
   * its own row, dated to that attempt, so the ledger keeps the whole history and the projector
   * takes the earliest.
   */
  def creditAssessments(userId: String, meta: CompetencyMeta, collectionId: String, batchId: String,
                        questionSetIds: List[String], ctx: RequestContext): Set[String] = {
    if (meta.isEmpty || questionSetIds.isEmpty) return Set.empty
    questionSetIds.flatMap { qs =>
      val attempts = assessmentService.getUserAssessments(userId, collectionId, batchId, qs, ctx).toList
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
            append(Evidence(
              userId = userId, skillId = code,
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
  def importExternal(userId: String, meta: CompetencyMeta, skillId: String, sourceId: String,
                     issuerId: Option[String], note: Option[String], occurredOn: Long,
                     ctx: RequestContext): Option[String] = {
    if (meta.isEmpty) return None
    if (!meta.isLeaf(skillId)) {
      logger.info(ctx, s"competency.ledger: import rejected, $skillId is not a leaf of ${meta.frameworkId}")
      return None
    }
    append(Evidence(
      userId = userId, skillId = skillId,
      evidenceId = AttainmentRules.evidenceId(occurredOn, SourceType.EXTERNAL, sourceId, ""),
      frameworkId = meta.frameworkId,
      sourceType = SourceType.EXTERNAL, sourceId = sourceId, batchId = "",
      score = None, maxScore = None,
      issuerId = issuerId, note = note, occurredOn = occurredOn), ctx)
    Some(skillId)
  }

  def revoke(userId: String, skillId: String, evidenceId: String, reason: String,
             ctx: RequestContext): Unit = {
    dao.revokeEvidence(userId, skillId, evidenceId, reason, ctx)
    logger.info(ctx, s"competency.ledger: revoked | user=$userId skill=$skillId evidence=$evidenceId")
  }

  def evidenceOf(userId: String, skillId: String, ctx: RequestContext): List[Evidence] =
    dao.evidenceOf(userId, skillId, ctx)

  private def append(e: Evidence, ctx: RequestContext): Unit = {
    dao.insertEvidence(e, ctx)
    logger.info(ctx, s"competency.ledger: append | user=${e.userId} skill=${e.skillId} " +
      s"source=${e.sourceType}:${e.sourceId}")
  }
}
