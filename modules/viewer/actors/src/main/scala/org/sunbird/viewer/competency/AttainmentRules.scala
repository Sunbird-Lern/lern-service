package org.sunbird.viewer.competency

import java.time.{Instant, ZoneOffset}
import java.time.format.DateTimeFormatter

/**
 * Every rule that decides what a learner holds. Pure: no Cassandra, no clock of its own, no config.
 * The projector supplies `now`; the ledger supplies the evidence.
 */
object AttainmentRules {

  val ATTAINED = "ATTAINED"
  val EXPIRING = "EXPIRING"
  val EXPIRED = "EXPIRED"
  val IN_PROGRESS = "IN_PROGRESS"

  private val bucketFmt = DateTimeFormatter.ofPattern("yyyy-MM").withZone(ZoneOffset.UTC)

  /**
   * Highest level whose cut-score and minimum-evidence bar are both met.
   * None when even the lowest band fails, which the ledger records as level index 0.
   */
  def band(pctScore: Double, evidenceCount: Int, levels: List[LevelDef]): Option[LevelDef] =
    levels.sortBy(-_.index).find(l => pctScore >= l.cutScore && evidenceCount >= l.minEvidenceCount)

  /** Percentage over the questions tagged with one competency. 0 when nothing was attempted. */
  def pct(score: Double, maxScore: Double): Double =
    if (maxScore <= 0) 0d else (score / maxScore) * 100d

  /** A completion-derived claim never exceeds the framework's cap. capIndex <= 0 means uncapped. */
  def capCompletion(claimedIndex: Int, capIndex: Int): Int =
    if (capIndex <= 0) claimedIndex else math.min(claimedIndex, capIndex)

  /** Evidence lapses at `expiresOn`; absent expiry never lapses. */
  def isExpired(e: Evidence, now: Long): Boolean = e.expiresOn.exists(_ <= now)

  /** occurredOn plus the level's validity, when the framework sets one. */
  def expiryOf(occurredOn: Long, validityMonths: Option[Int]): Option[Long] =
    validityMonths.filter(_ > 0).map { m =>
      Instant.ofEpochMilli(occurredOn).atZone(ZoneOffset.UTC).plusMonths(m.toLong).toInstant.toEpochMilli
    }

  /** Partition key for competency_expiry_index. */
  def expiryBucket(expiresOn: Long): String = bucketFmt.format(Instant.ofEpochMilli(expiresOn))

  /**
   * The passbook entry implied by a competency's evidence.
   *
   * Held level is the maximum over live evidence, so a later weaker attempt never demotes a learner.
   * Expiry is the only decrement: once every supporting row has lapsed the entry falls back to the
   * lapsed claim and is marked EXPIRED rather than deleted, so the history survives.
   * None means no claim at all — every row revoked.
   */
  def project(evidence: List[Evidence], now: Long, expiringWindowMillis: Long): Option[PassbookEntry] = {
    val live = evidence.filterNot(_.revoked)
    if (live.isEmpty) return None
    val (current, lapsed) = live.partition(e => !isExpired(e, now))
    val (governing, status) =
      if (current.nonEmpty) {
        val b = best(current)
        val s =
          if (b.levelIndex <= 0) IN_PROGRESS
          else if (b.expiresOn.exists(_ <= now + expiringWindowMillis)) EXPIRING
          else ATTAINED
        (b, s)
      } else (best(lapsed), EXPIRED)
    Some(PassbookEntry(
      competencyId = governing.competencyId,
      frameworkId = governing.frameworkId,
      level = governing.level,
      levelIndex = governing.levelIndex,
      status = status,
      sourceType = governing.sourceType,
      governingEvidenceId = governing.evidenceId,
      attainedOn = governing.occurredOn,
      expiresOn = governing.expiresOn))
  }

  /** Highest level wins; the earliest attempt at that level wins the tie, so attainedOn is stable. */
  private def best(xs: List[Evidence]): Evidence =
    xs.sortBy(e => (-e.levelIndex, e.occurredOn)).head

  /**
   * Deterministic ledger id: time-ordered and idempotent. Replaying the same completion rewrites
   * the same row instead of appending a duplicate.
   */
  def evidenceId(occurredOn: Long, sourceType: String, sourceId: String, batchId: String): String = {
    val digest = math.abs(s"$sourceType|$sourceId|$batchId".hashCode).toString
    f"$occurredOn%019d:$digest"
  }
}
