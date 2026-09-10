package org.sunbird.viewer.competency

/**
 * Required set versus held set. Pure: takes a role's leaf skills and the learner's held skills,
 * returns the gap and a readiness figure.
 *
 * Set arithmetic on two lists of codes. There is no level comparison, so a skill is met or missing
 * and nothing sits in between.
 */
object GapCalculator {

  val MET = "MET"
  val MISSING = "MISSING"

  /** One row per required skill, ordered by code so the output is stable across calls. */
  def rows(required: Set[String], held: Set[String]): List[GapRow] =
    required.toList.sorted.map(s => GapRow(s, if (held.contains(s)) MET else MISSING))

  /**
   * Met over required, as a whole percentage. 100 when the role requires nothing, which keeps a
   * role with an empty skill list from reading as a total gap.
   */
  def readiness(rows: List[GapRow]): Int =
    if (rows.isEmpty) 100 else rows.count(_.status == MET) * 100 / rows.size

  /** Skills still to earn. Drives the recommendation query. */
  def outstanding(rows: List[GapRow]): List[String] =
    rows.filter(_.status == MISSING).map(_.skillId)

  def met(rows: List[GapRow]): List[String] =
    rows.filter(_.status == MET).map(_.skillId)
}
