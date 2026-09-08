package org.sunbird.viewer.competency

import org.sunbird.assessment.service.CassandraService
import org.sunbird.logging.LoggerUtil
import org.sunbird.request.RequestContext

/**
 * Appends facts. Never interprets held level — that is the projector's job.
 *
 * Every write is idempotent: the evidence id is derived from (occurredOn, sourceType, sourceId,
 * batchId), so replaying a completion or an aggregate rewrites the same row rather than appending a
 * duplicate. Each method returns the competency ids it touched, for the caller to project.
 */
class CompetencyLedger(dao: CompetencyDao,
                       frameworkUtil: CompetencyFrameworkUtil,
                       assessmentService: CassandraService) {

  private val logger = new LoggerUtil(classOf[CompetencyLedger])

  /** Completing a course claims its tagged competencies, capped at maxCompletionDerivedLevel. */
  def creditCourse(userId: String, meta: CompetencyMeta, courseId: String, batchId: String,
                   completedOn: Long, ctx: RequestContext): Set[String] =
    creditCompletion(userId, meta, courseId, batchId, SourceType.COURSE, completedOn, ctx)

  /** Completing a Learning Path claims the competencies the programme itself declares. */
  def creditCollection(userId: String, meta: CompetencyMeta, rootId: String, batchId: String,
                       completedOn: Long, ctx: RequestContext): Set[String] =
    creditCompletion(userId, meta, rootId, batchId, SourceType.LEARNING_PATH, completedOn, ctx)

  private def creditCompletion(userId: String, meta: CompetencyMeta, nodeId: String, batchId: String,
                               sourceType: String, completedOn: Long, ctx: RequestContext): Set[String] = {
    if (meta.isEmpty) return Set.empty
    val claims = frameworkUtil.claimsOf(List(nodeId), ctx).getOrElse(nodeId, Nil)
    claims.flatMap { claim =>
      meta.levelByCode(claim.levelCode).map { level =>
        val capped = AttainmentRules.capCompletion(level.index, meta.maxCompletionDerivedIndex)
        val effective = meta.levels.find(_.index == capped).getOrElse(level)
        val expiry = AttainmentRules.expiryOf(completedOn, frameworkUtil.validityMonthsFor(claim.code, effective, meta))
        append(Evidence(
          userId = userId, competencyId = claim.code,
          evidenceId = AttainmentRules.evidenceId(completedOn, sourceType, nodeId, batchId),
          frameworkId = meta.frameworkId, level = effective.code, levelIndex = effective.index,
          sourceType = sourceType, sourceId = nodeId, batchId = batchId,
          score = None, maxScore = None, evidenceCount = 0,
          issuerId = None, note = None, occurredOn = completedOn, expiresOn = expiry), ctx)
        claim.code
      }
    }.toSet
  }

  /**
   * Bands each competency over the questions tagged with it, in the learner's best attempt.
   *
   * Sub-threshold results are still recorded, at level index 0, so the passbook can show
   * IN_PROGRESS rather than nothing.
   */
  def creditAssessments(userId: String, meta: CompetencyMeta, collectionId: String, batchId: String,
                        questionSetIds: List[String], ctx: RequestContext): Set[String] = {
    if (meta.isEmpty || questionSetIds.isEmpty) return Set.empty
    questionSetIds.flatMap { qs =>
      val attempts = assessmentService.getUserAssessments(userId, collectionId, batchId, qs, ctx)
      if (attempts.isEmpty) Set.empty[String]
      else {
        val bestAttempt = attempts.maxBy(_.totalScore)
        val answered = bestAttempt.questions
        if (answered.isEmpty) Set.empty[String]
        else {
          val claims = frameworkUtil.claimsOf(answered.map(_.questionId), ctx)
          // The CLAIMED level is carried through the grouping, not discarded. A question tagged
          // `health-data-reporting @ l1` asserts competence at l1 -- answering it cannot prove l4.
          // Dropping the level made the granted level depend only on the band, so a single correct
          // answer on an l1 question awarded the top level of the scale.
          val byCompetency = answered.flatMap(q =>
            claims.getOrElse(q.questionId, Nil).map(c => c.code -> (q, c.levelCode))).groupBy(_._1)
              .map { case (code, xs) => code -> xs.map(_._2) }
          val occurredOn = if (bestAttempt.lastAttemptedOn > 0) bestAttempt.lastAttemptedOn else System.currentTimeMillis()
          byCompetency.map { case (code, entries) =>
            val qs2 = entries.map(_._1)
            val score = qs2.map(_.score).sum
            val maxScore = qs2.map(_.maxScore).sum
            val pct = AttainmentRules.pct(score, maxScore)
            // Highest level these questions actually claimed; 0 when none resolves, which
            // capCompletion treats as uncapped so an untagged level cannot block crediting.
            val claimCap = entries.flatMap { case (_, lvl) => meta.levelByCode(lvl).map(_.index) }
              .foldLeft(0)(math.max)
            val band = AttainmentRules.band(pct, qs2.size, meta.levels)
              .map { b =>
                val capped = AttainmentRules.capCompletion(b.index, claimCap)
                meta.levels.find(_.index == capped).getOrElse(b)
              }
            val expiry = band.flatMap(l =>
              AttainmentRules.expiryOf(occurredOn, frameworkUtil.validityMonthsFor(code, l, meta)))
            append(Evidence(
              userId = userId, competencyId = code,
              evidenceId = AttainmentRules.evidenceId(occurredOn, SourceType.ASSESSMENT, qs, batchId),
              frameworkId = meta.frameworkId,
              level = band.map(_.code).getOrElse(""), levelIndex = band.map(_.index).getOrElse(0),
              sourceType = SourceType.ASSESSMENT, sourceId = qs, batchId = batchId,
              score = Some(score), maxScore = Some(maxScore), evidenceCount = qs2.size,
              issuerId = None, note = None, occurredOn = occurredOn, expiresOn = expiry), ctx)
            code
          }.toSet
        }
      }
    }.toSet
  }

  /** Registers a credential earned outside the platform. */
  def importExternal(userId: String, meta: CompetencyMeta, competencyId: String, levelCode: String,
                     sourceId: String, issuerId: Option[String], note: Option[String],
                     occurredOn: Long, explicitExpiry: Option[Long], ctx: RequestContext): Option[String] =
    meta.levelByCode(levelCode).map { level =>
      val expiry = explicitExpiry.orElse(
        AttainmentRules.expiryOf(occurredOn, frameworkUtil.validityMonthsFor(competencyId, level, meta)))
      append(Evidence(
        userId = userId, competencyId = competencyId,
        evidenceId = AttainmentRules.evidenceId(occurredOn, SourceType.EXTERNAL, sourceId, ""),
        frameworkId = meta.frameworkId, level = level.code, levelIndex = level.index,
        sourceType = SourceType.EXTERNAL, sourceId = sourceId, batchId = "",
        score = None, maxScore = None, evidenceCount = 0,
        issuerId = issuerId, note = note, occurredOn = occurredOn, expiresOn = expiry), ctx)
      competencyId
    }

  def revoke(userId: String, competencyId: String, evidenceId: String, reason: String,
             ctx: RequestContext): Unit = {
    dao.revokeEvidence(userId, competencyId, evidenceId, reason, ctx)
    logger.info(ctx, s"competency.ledger: revoked | user=$userId competency=$competencyId evidence=$evidenceId")
  }

  def evidenceOf(userId: String, competencyId: String, ctx: RequestContext): List[Evidence] =
    dao.evidenceOf(userId, competencyId, ctx)

  private def append(e: Evidence, ctx: RequestContext): Unit = {
    dao.insertEvidence(e, ctx)
    logger.info(ctx, s"competency.ledger: append | user=${e.userId} competency=${e.competencyId} " +
      s"level=${e.level}(${e.levelIndex}) source=${e.sourceType}:${e.sourceId}")
  }
}
