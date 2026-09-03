package org.sunbird.viewer.competency

/**
 * Required set versus held set. Pure: takes the position's requirements and the learner's passbook
 * levels, returns the gap and a readiness figure.
 */
object GapCalculator {

  val MET = "MET"
  val BELOW = "BELOW"
  val MISSING = "MISSING"

  /**
   * One row per requirement. `held` maps competency id to the level index currently held;
   * an absent or expired competency is index 0.
   */
  def rows(requirements: List[RequirementDef], held: Map[String, (String, Int)]): List[GapRow] =
    requirements.map { r =>
      val (heldLevel, heldIndex) = held.getOrElse(r.competencyId, ("", 0))
      val status =
        if (heldIndex >= r.requiredLevelIndex && heldIndex > 0) MET
        else if (heldIndex > 0) BELOW
        else MISSING
      GapRow(r.competencyId, r.requiredLevel, r.requiredLevelIndex,
        heldLevel, heldIndex, r.criticality, status)
    }

  /**
   * Met mandatory requirements over all mandatory requirements. Desirable rows are reported but do
   * not move the figure — counting them puts every readiness score in the seventies.
   * 100 when a position declares no mandatory requirements.
   */
  def readiness(rows: List[GapRow]): Int = {
    val mandatory = rows.filter(isMandatory)
    if (mandatory.isEmpty) 100
    else mandatory.count(_.status == MET) * 100 / mandatory.size
  }

  def isMandatory(r: GapRow): Boolean =
    r.criticality == null || r.criticality.isEmpty || Criticality.MANDATORY.equalsIgnoreCase(r.criticality)

  /** Competencies still to earn, most critical first. Drives the recommendation query. */
  def outstanding(rows: List[GapRow]): List[GapRow] =
    rows.filter(_.status != MET).sortBy(r => (if (isMandatory(r)) 0 else 1, r.status == BELOW, r.competencyId))
}
