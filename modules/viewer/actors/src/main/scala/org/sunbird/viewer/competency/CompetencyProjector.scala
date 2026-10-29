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
   * Runs a ledger write and issues or revokes badges for what it changed.
   *
   * The write gets a lazy `LedgerSnapshot` of the learner's evidence, which it checks and updates.
   * The profile before and after is computed from that snapshot, so a badge is issued on the
   * transition into held, not on every event that re-credits an already-held skill, and a replay
   * issues nothing. A write that touches no skill never loads the snapshot, so it reads nothing.
   */
  def recording(userId: String, ctx: RequestContext)(write: LedgerSnapshot => Set[String]): Recorded = {
    val snap = new LedgerSnapshot(userId, dao.evidenceOfUser(userId, ctx))
    val touched = write(snap).filter(s => s != null && s.nonEmpty)
    if (touched.isEmpty) return Recorded(touched, snap)

    val before = AttainmentRules.profile(snap.before)
    val after = AttainmentRules.profile(snap.current)
    val (attained, lost) = AttainmentRules.transitions(before.keySet, after.keySet)
    attained.foreach(sid => badges.issue(userId, after(sid), ctx))
    lost.foreach(sid => badges.revoke(userId, sid, before(sid).frameworkId, ctx))

    if (attained.nonEmpty || lost.nonEmpty)
      logger.info(ctx, s"competency.projector: user=$userId held=${after.size} " +
        s"attained=[${attained.toList.sorted.mkString(",")}] lost=[${lost.toList.sorted.mkString(",")}]")
    Recorded(touched, snap)
  }

  /**
   * Re-derives evidence from what the learner actually did, so a skill added to the framework after
   * the fact picks up credit from completions and attempts that predate it. Badges follow from the
   * resulting change. Safe to re-run: every source but EXTERNAL is deduped. Returns how many skills
   * are held afterwards.
   */
  def reproject(userId: String, ctx: RequestContext): Int = {
    val r = recording(userId, ctx)(rederive(_, ctx))
    val heldAfter = AttainmentRules.profile(r.snapshot.current).size
    logger.info(ctx, s"competency.projector: reproject | user=$userId rederived=${r.touched.size} held=$heldAfter")
    heldAfter
  }

  /** Walks the learner's enrolments and re-appends the evidence each one implies. */
  private def rederive(snap: LedgerSnapshot, ctx: RequestContext): Set[String] = {
    val userId = snap.userId
    val frameworkOfCollection = scala.collection.mutable.Map[String, Option[String]]()
    dao.enrolmentsOf(userId, ctx).flatMap { enrol =>
      val fwId = frameworkOfCollection.getOrElseUpdate(enrol.courseId,
        frameworkUtil.frameworkOf(enrol.courseId, ctx))
      fwId.map(frameworkUtil.meta(_, ctx)).filterNot(_.isEmpty).toList.flatMap { meta =>
        val completedOn = if (enrol.completedOn > 0) enrol.completedOn else System.currentTimeMillis()
        val fromCompletion =
          if (enrol.status == 2) ledger.creditCourse(snap, meta, enrol.courseId, enrol.batchId, completedOn, ctx)
          else Set.empty[String]
        val questionSets = dao.assessedContentIds(userId, enrol.courseId, enrol.batchId, ctx)
        val fromAssessment =
          ledger.creditAssessments(snap, meta, enrol.courseId, enrol.batchId, questionSets, ctx)
        fromCompletion ++ fromAssessment
      }
    }.toSet
  }
}

/** What a ledger write did: the skills it touched, and the snapshot as the write left it. */
case class Recorded(touched: Set[String], snapshot: LedgerSnapshot)
