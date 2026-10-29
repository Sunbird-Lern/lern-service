package org.sunbird.viewer.competency

import scala.collection.mutable

/**
 * One learner's evidence, read at most once per ledger write and kept current as the write
 * proceeds.
 *
 * The ledger checks duplicates against it instead of re-reading a skill per append, and records
 * every insert and revocation in it after the Cassandra write. The projector then computes the
 * profile before and after from memory, so one credit costs one partition read however many skills
 * it touches.
 *
 * LAZY: nothing is read until the ledger first needs it. A write with nothing to credit - a course
 * that teaches no skills, an attempt that earned none - costs no read at all, which matters because
 * every aggregation re-credits every completed course. The ledger must therefore touch the snapshot
 * BEFORE its own Cassandra write, so `before` is the state the write started from; `append` does it
 * through `ofSkill`, `revoke` through `load`.
 *
 * Not a lock. A concurrent write for the same learner is not seen, exactly as with a fresh read.
 */
final class LedgerSnapshot(val userId: String, read: => List[Evidence]) {

  private var reads = 0

  /** The rows as first read. Untouched by later appends and revocations. */
  lazy val before: List[Evidence] = { reads += 1; read }

  private lazy val rows = mutable.LinkedHashMap[(String, String), Evidence](
    before.map(e => (e.skillId, e.evidenceId) -> e): _*)

  /** Whether the learner's evidence has been read. False when the write found nothing to do. */
  def isLoaded: Boolean = reads > 0

  /** Every row as it stands now: the read, plus what this write has appended or revoked. */
  def current: List[Evidence] = rows.values.toList

  def ofSkill(skillId: String): List[Evidence] = rows.values.filter(_.skillId == skillId).toList

  /** Forces the read. For a write that would otherwise reach Cassandra before touching the snapshot. */
  private[competency] def load(): Unit = rows

  private[competency] def added(e: Evidence): Unit = rows((e.skillId, e.evidenceId)) = e

  private[competency] def revoked(skillId: String, evidenceId: String, reason: String): Unit =
    rows.get((skillId, evidenceId)).foreach { e =>
      rows((skillId, evidenceId)) = e.copy(revoked = true, revokedReason = Some(reason))
    }
}
