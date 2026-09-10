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

  // ---- recommendation -------------------------------------------------------------------------

  /**
   * Candidates ranked against one learner's gap.
   *
   * Ordered by gap skills covered (most first), then by how much of the candidate the learner has
   * already covered (least first), then by total size so a tight fit beats a sprawling one, then
   * by id so the order is stable across calls.
   *
   * `alreadyHeld` stands in for effort after waiving. It is a proxy, not a course count: a
   * candidate whose skills the learner mostly holds is one that waiving would mostly skip, and
   * counting its actual remaining courses would need each candidate's hierarchy.
   *
   * A candidate covering none of the gap is dropped — recommending it would be noise.
   */
  def rankCandidates(candidates: List[Candidate], outstanding: Set[String],
                     held: Set[String]): List[RankedCandidate] =
    candidates
      .map(c => RankedCandidate(
        candidate = c,
        gapCovered = c.skills.intersect(outstanding).size,
        alreadyHeld = c.skills.intersect(held).size,
        totalSkills = c.skills.size))
      .filter(_.gapCovered > 0)
      .sortBy(r => (-r.gapCovered, r.alreadyHeld, r.totalSkills, r.candidate.id))

  // ---- coverage report ------------------------------------------------------------------------

  val COVERED = "COVERED"
  val NOT_COVERED = "NOT_COVERED"

  /**
   * Does anything in the programme teach each skill the role requires?
   *
   * `skillsByCourse` maps each course in the programme to the leaf skills it teaches. A skill no
   * course teaches is reported, not raised: the check informs the author, it does not block them.
   */
  def coverage(required: Set[String],
               skillsByCourse: Map[String, List[String]]): List[CoverageRow] =
    required.toList.sorted.map { skill =>
      val taughtBy = skillsByCourse.collect { case (course, skills) if skills.contains(skill) => course }
        .toList.sorted
      CoverageRow(skill, taughtBy, if (taughtBy.nonEmpty) COVERED else NOT_COVERED)
    }

  /**
   * Skills the programme teaches but no question in it measures.
   *
   * Each can only ever be completion-derived, which is the weakest evidence the model accepts.
   */
  def unassessed(taught: Set[String], assessed: Set[String]): List[String] =
    taught.diff(assessed).toList.sorted
}
