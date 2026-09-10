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
                          frameworkUtil: CompetencyFrameworkUtil) {

  private val logger = new LoggerUtil(classOf[CompetencyProjector])

  /** Recomputes the given skills for one learner. Returns how many are held afterwards. */
  def project(userId: String, skillIds: Set[String], ctx: RequestContext): Int =
    skillIds.filter(_ != null).filter(_.nonEmpty).count { sid =>
      AttainmentRules.project(dao.evidenceOf(userId, sid, ctx)) match {
        case Some(entry) =>
          dao.upsertSkill(userId, entry, ctx)
          logger.info(ctx, s"competency.projector: held | user=$userId skill=$sid " +
            s"source=${entry.sourceType}")
          true
        case None =>
          // every supporting row revoked: the skill is no longer held, so the row goes.
          // The evidence stays, so a later un-revocation reprojects it back.
          dao.deleteSkill(userId, sid, ctx)
          logger.info(ctx, s"competency.projector: not held | user=$userId skill=$sid")
          false
      }
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
