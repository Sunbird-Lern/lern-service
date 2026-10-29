package org.sunbird.viewer.competency

import org.sunbird.logging.LoggerUtil
import org.sunbird.request.RequestContext

/**
 * Derives the skill profile from the ledger, and turns changes in it into badge events.
 *
 * Nothing here is stored: the profile is recomputed from the learner's evidence partition on every
 * read, so it cannot drift from the ledger and there is nothing to rebuild.
 */
class CompetencyProjector(dao: CompetencyDao,
                          ledger: CompetencyLedger,
                          frameworkUtil: CompetencyFrameworkUtil,
                          badges: SkillBadgeUtil = SkillBadgeUtil()) {

  private val logger = new LoggerUtil(classOf[CompetencyProjector])

  /** Every skill the learner holds, keyed by skill id. One partition read. */
  def held(userId: String, ctx: RequestContext): Map[String, SkillEntry] =
    AttainmentRules.profile(dao.evidenceOfUser(userId, ctx))

  /**
   * Runs a ledger write and issues or revokes badges for what it changed. Returns the skill ids the
   * write touched.
   *
   * The profile is read before and after the write, so a badge is issued on the transition into
   * held, not on every event that re-credits an already-held skill. A replay therefore issues
   * nothing. When the write touches no skill, nothing is re-read.
   */
  def recording(userId: String, ctx: RequestContext)(write: => Set[String]): Set[String] = {
    val before = held(userId, ctx)
    val touched = write.filter(s => s != null && s.nonEmpty)
    if (touched.isEmpty) return touched

    val after = held(userId, ctx)
    val (attained, lost) = AttainmentRules.transitions(before.keySet, after.keySet)
    attained.foreach(sid => badges.issue(userId, after(sid), ctx))
    lost.foreach(sid => badges.revoke(userId, sid, before(sid).frameworkId, ctx))

    if (attained.nonEmpty || lost.nonEmpty)
      logger.info(ctx, s"competency.projector: user=$userId held=${after.size} " +
        s"attained=[${attained.toList.sorted.mkString(",")}] lost=[${lost.toList.sorted.mkString(",")}]")
    touched
  }

  /**
   * Re-derives evidence from what the learner actually did, so a skill added to the framework after
   * the fact picks up credit from completions and attempts that predate it. Badges follow from the
   * resulting change. Safe to re-run: completions are deduped on their source. Returns how many
   * skills are held afterwards.
   */
  def reproject(userId: String, ctx: RequestContext): Int = {
    val rederived = recording(userId, ctx)(rederive(userId, ctx))
    val heldAfter = held(userId, ctx).size
    logger.info(ctx, s"competency.projector: reproject | user=$userId rederived=${rederived.size} held=$heldAfter")
    heldAfter
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
