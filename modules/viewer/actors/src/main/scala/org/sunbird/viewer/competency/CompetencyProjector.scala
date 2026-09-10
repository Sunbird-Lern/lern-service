package org.sunbird.viewer.competency

import org.sunbird.logging.LoggerUtil
import org.sunbird.request.RequestContext

/**
 * Derives the skill profile from the ledger. The only writer of user_skill.
 *
 * Nothing here accumulates: each run recomputes an entry as a function of that skill's evidence
 * rows, so the profile can be dropped and rebuilt, and a repeated event is harmless.
 */
class CompetencyProjector(dao: CompetencyDao,
                          ledger: CompetencyLedger,
                          frameworkUtil: CompetencyFrameworkUtil,
                          badges: SkillBadgeUtil = SkillBadgeUtil()) {

  private val logger = new LoggerUtil(classOf[CompetencyProjector])

  /**
   * Recomputes the given skills for one learner. Returns how many are held afterwards.
   *
   * Reads the profile once up front so a badge is issued on the transition into held, not on every
   * event that re-credits an already-held skill. A replay therefore issues nothing.
   */
  def project(userId: String, skillIds: Set[String], ctx: RequestContext): Int = {
    val wanted = skillIds.filter(s => s != null && s.nonEmpty)
    if (wanted.isEmpty) return 0

    val beforeRows = dao.profileOf(userId, ctx)
    val before = beforeRows.map(_.skillId).toSet
    val beforeById = beforeRows.map(e => e.skillId -> e).toMap

    val held = wanted.toList.flatMap { sid =>
      AttainmentRules.project(dao.evidenceOf(userId, sid, ctx)).map(sid -> _)
    }.toMap

    held.foreach { case (_, entry) => dao.upsertSkill(userId, entry, ctx) }
    // every supporting row revoked: the skill is no longer held, so the row goes. The evidence
    // stays, so a later un-revocation reprojects it back.
    wanted.diff(held.keySet).foreach(sid => dao.deleteSkill(userId, sid, ctx))

    val after = before.diff(wanted) ++ held.keySet
    val (attained, lost) = AttainmentRules.transitions(before, after)
    attained.foreach(sid => badges.issue(userId, held(sid), ctx))
    lost.foreach(sid => badges.revoke(userId, sid, beforeById.get(sid).map(_.frameworkId).getOrElse(""), ctx))

    if (attained.nonEmpty || lost.nonEmpty)
      logger.info(ctx, s"competency.projector: user=$userId held=${held.size} " +
        s"attained=[${attained.toList.sorted.mkString(",")}] lost=[${lost.toList.sorted.mkString(",")}]")
    held.size
  }

  /**
   * Rebuilds a learner's whole profile.
   *
   * Re-derives evidence from what the learner actually did before projecting, so a skill added to
   * the framework after the fact picks up credit from attempts that predate it. Safe to re-run:
   * ledger writes are idempotent.
   */
  def reproject(userId: String, ctx: RequestContext): Int = {
    val rederived = rederive(userId, ctx)
    val existing = dao.profileOf(userId, ctx).map(_.skillId).toSet
    val touched = rederived ++ existing
    logger.info(ctx, s"competency.projector: reproject | user=$userId " +
      s"rederived=${rederived.size} total=${touched.size}")
    project(userId, touched, ctx)
  }

  /** Walks the learner's enrolments and re-appends the evidence each one implies. */
  private def rederive(userId: String, ctx: RequestContext): Set[String] = {
    val frameworkOfCollection = scala.collection.mutable.Map[String, Option[String]]()
    dao.enrolmentsOf(userId, ctx).flatMap { enrol =>
      val fwId = frameworkOfCollection.getOrElseUpdate(enrol.courseId,
        frameworkUtil.frameworkOf(enrol.courseId, ctx))
      fwId.map(frameworkUtil.meta(_, ctx)).filterNot(_.isEmpty).toList.flatMap { meta =>
        val completedOn = if (enrol.completedOn > 0) enrol.completedOn else System.currentTimeMillis()
        val fromCompletion =
          if (enrol.status == 2) ledger.creditCourse(userId, meta, enrol.courseId, enrol.batchId, completedOn, ctx)
          else Set.empty[String]
        val questionSets = dao.assessedContentIds(userId, enrol.courseId, enrol.batchId, ctx)
        val fromAssessment =
          ledger.creditAssessments(userId, meta, enrol.courseId, enrol.batchId, questionSets, ctx)
        fromCompletion ++ fromAssessment
      }
    }.toSet
  }
}
