package org.sunbird.viewer.competency

/**
 * Every rule that decides what a learner holds. Pure: no Cassandra, no clock, no config.
 *
 * A skill is held or not held. There is no scale, so no banding, no cap and no comparison; and no
 * expiry, so nothing ever decrements a profile except revocation.
 */
object AttainmentRules {

  /**
   * The profile entry implied by one skill's evidence.
   *
   * Held is set membership: one live row is enough. None means no claim at all, which after a
   * revocation is the projector's signal to delete the row rather than downgrade it.
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
   * Deterministic ledger id: time-ordered and idempotent. Replaying the same completion rewrites
   * the same row instead of appending a duplicate.
   */
  def evidenceId(occurredOn: Long, sourceType: String, sourceId: String, batchId: String): String = {
    val digest = math.abs(s"$sourceType|$sourceId|$batchId".hashCode).toString
    f"$occurredOn%019d:$digest"
  }
}
