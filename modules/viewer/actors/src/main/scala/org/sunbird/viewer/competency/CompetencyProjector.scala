package org.sunbird.viewer.competency

import org.sunbird.common.ProjectUtil
import org.sunbird.logging.LoggerUtil
import org.sunbird.request.RequestContext

import java.time.{Instant, ZoneOffset}

/**
 * Derives the passbook from the ledger. The only writer of user_competency.
 *
 * Nothing here accumulates: each run recomputes an entry as a function of that competency's
 * evidence rows, so the passbook can be dropped and rebuilt, and a repeated event is harmless.
 */
class CompetencyProjector(dao: CompetencyDao,
                          ledger: CompetencyLedger,
                          frameworkUtil: CompetencyFrameworkUtil) {

  private val logger = new LoggerUtil(classOf[CompetencyProjector])

  private val expiringWindowMillis: Long =
    Option(ProjectUtil.getConfigValue("competency_expiring_window_days"))
      .map(_.trim).filter(_.nonEmpty).map(_.toLong).getOrElse(30L) * 24L * 3600L * 1000L

  /** Recomputes the given competencies for one learner. Returns how many entries were written. */
  def project(userId: String, competencyIds: Set[String], ctx: RequestContext): Int = {
    val now = System.currentTimeMillis()
    competencyIds.filter(_.nonEmpty).count { cid =>
      val evidence = dao.evidenceOf(userId, cid, ctx)
      AttainmentRules.project(evidence, now, expiringWindowMillis) match {
        case Some(entry) =>
          dao.upsertPassbook(userId, entry, ctx)
          entry.expiresOn.foreach(exp => dao.indexExpiry(userId, cid, exp, ctx))
          logger.info(ctx, s"competency.projector: ${entry.status} | user=$userId competency=$cid " +
            s"level=${entry.level}(${entry.levelIndex})")
          true
        case None =>
          // every row revoked: keep the row, drop the claim to nothing
          dao.setPassbookStatus(userId, cid, AttainmentRules.IN_PROGRESS, ctx)
          false
      }
    }
  }

  /**
   * Rebuilds a learner's whole passbook.
   *
   * Re-derives evidence from what the learner actually did before projecting, so a competency added
   * to the framework after the fact picks up credit from attempts that predate it. Safe to re-run:
   * ledger writes are idempotent.
   */
  def reproject(userId: String, ctx: RequestContext): Int = {
    val rederived = rederive(userId, ctx)
    val existing = dao.passbookOf(userId, ctx).map(_.competencyId).toSet
    val touched = rederived ++ existing
    logger.info(ctx, s"competency.projector: reproject | user=$userId rederived=${rederived.size} total=${touched.size}")
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

  /**
   * Moves lapsing and lapsed entries to their correct status.
   *
   * Reprojects every entry indexed in the previous, current and next expiry buckets; reprojection
   * already decides EXPIRING versus EXPIRED against the clock, so the sweep only has to choose
   * which rows to revisit.
   */
  def sweep(ctx: RequestContext): Int = {
    val now = System.currentTimeMillis()
    val buckets = bucketsAround(now)
    val due = buckets.flatMap(b => dao.expiriesIn(b, ctx))
      .filter { case (_, _, expiresOn) => expiresOn <= now + expiringWindowMillis }
    logger.info(ctx, s"competency.sweep: buckets=[${buckets.mkString(",")}] due=${due.size}")
    due.groupBy(_._1).map { case (userId, rows) =>
      project(userId, rows.map(_._2).toSet, ctx)
    }.sum
  }

  private def bucketsAround(now: Long): List[String] = {
    val month = Instant.ofEpochMilli(now).atZone(ZoneOffset.UTC)
    List(-1, 0, 1).map(d => AttainmentRules.expiryBucket(month.plusMonths(d.toLong).toInstant.toEpochMilli))
  }
}
