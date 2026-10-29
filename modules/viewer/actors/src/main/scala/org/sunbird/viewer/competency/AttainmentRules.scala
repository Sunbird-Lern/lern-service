package org.sunbird.viewer.competency

/**
 * Every rule that decides what a learner holds. Pure: no Cassandra, no clock, no config.
 *
 * A skill is held or not held. There is no scale, so no banding, no cap and no comparison; and no
 * expiry, so nothing ever decrements a profile except revocation.
 */
object AttainmentRules {

  /**
   * A learner's profile: one entry per skill with at least one live row, keyed by skill.
   * Nothing stores this; it is recomputed from the ledger on every read.
   */
  def profile(evidence: List[Evidence]): Map[String, SkillEntry] =
    evidence.groupBy(_.skillId).flatMap { case (sid, rows) => project(rows).map(sid -> _) }

  /**
   * The profile entry implied by one skill's evidence.
   *
   * Held is set membership: one live row is enough. None means not held - every row is revoked,
   * or there are none.
   */
  def project(evidence: List[Evidence]): Option[SkillEntry] =
    live(evidence) match {
      case Nil => None
      case rows =>
        val g = governing(rows)
        Some(SkillEntry(
          skillId = g.skillId,
          frameworkId = g.frameworkId,
          sourceType = g.sourceType,
          governingEvidenceId = g.evidenceId,
          attainedOn = g.occurredOn))
    }

  def live(evidence: List[Evidence]): List[Evidence] = evidence.filterNot(_.revoked)

  def isHeld(evidence: List[Evidence]): Boolean = live(evidence).nonEmpty

  /**
   * The earliest live row wins, so `attainedOn` is the date the learner first earned the skill and
   * does not move when later evidence arrives. `evidenceId` breaks a same-millisecond tie, which
   * keeps reprojection deterministic.
   */
  private def governing(rows: List[Evidence]): Evidence =
    rows.sortBy(e => (e.occurredOn, e.evidenceId)).head

  /**
   * Assessment attainment: every question tagged with the skill answered at full marks.
   *
   * Each pair is one question's (score, maxScore). A question with no marks available cannot
   * evidence anything, so it fails the test rather than passing it vacuously. An empty list is
   * not full marks — nothing was asked.
   */
  def fullMarks(questions: List[(Double, Double)]): Boolean =
    questions.nonEmpty && questions.forall { case (score, maxScore) =>
      maxScore > 0d && score >= maxScore
    }

  /**
   * Skills earned in one attempt: those whose every tagged question is at full marks.
   *
   * `questionsBySkill` is that attempt's questions grouped by the skill they are tagged with, as
   * (score, maxScore) pairs. A question tagged with two skills counts toward both.
   */
  def earnedIn(questionsBySkill: Map[String, List[(Double, Double)]]): Set[String] =
    questionsBySkill.collect { case (skill, questions) if fullMarks(questions) => skill }.toSet

  /**
   * Skills earned across every attempt, unioned.
   *
   * Attempts are not reduced to a best one. Under binary attainment "best" is not a meaningful
   * unit: a skill answered perfectly in the first attempt would go uncredited if a later attempt
   * scored higher overall but got that skill wrong.
   */
  def earnedAcross(attempts: List[Map[String, List[(Double, Double)]]]): Set[String] =
    attempts.flatMap(earnedIn).toSet

  /**
   * What changed between two profiles: skills newly held, and skills no longer held.
   *
   * This is what makes badge issuance idempotent. A replayed completion produces the same profile,
   * so both sets come back empty and no second badge is issued.
   */
  def transitions(before: Set[String], after: Set[String]): (Set[String], Set[String]) =
    (after.diff(before), before.diff(after))

  /**
   * Ledger id: time-ordered within a skill. Not idempotent on its own, since `occurredOn` is the
   * moment of crediting for live completions; `isDuplicate` is what stops a replay.
   */
  def evidenceId(occurredOn: Long, sourceType: String, sourceId: String, batchId: String): String = {
    val digest = math.abs(s"$sourceType|$sourceId|$batchId".hashCode).toString
    f"$occurredOn%019d:$digest"
  }

  /**
   * Whether this source has already evidenced the skill. `existing` is that one skill's rows.
   *
   * `evidenceId` embeds a moment - the crediting time for a completion, the attempt time for an
   * assessment - so the same source credited twice yields two ids and two rows: the ledger grows on
   * every rollup re-run, and with every further passing attempt. Held needs one live row, and the
   * profile reads the earliest, so the source triple is the real identity.
   *
   * Assessments are deduped per question set, not per attempt. Crediting across attempts is
   * unaffected: the check is per skill, so a skill first earned in a later attempt still has no row
   * and is recorded.
   *
   * EXTERNAL is excluded: its sourceId defaults to "external", so two different credentials for one
   * skill would collide.
   */
  def isDuplicate(existing: Seq[(String, String, String, Boolean)],
                  sourceType: String, sourceId: String, batchId: String): Boolean =
    sourceType != SourceType.EXTERNAL && existing.exists { case (t, id, b, revoked) =>
      !revoked && t == sourceType && id == sourceId && b == batchId
    }
}
