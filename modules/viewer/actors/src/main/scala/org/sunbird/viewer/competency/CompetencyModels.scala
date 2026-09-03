package org.sunbird.viewer.competency

/** A level in the framework's proficiency scale. `index` is the only ordering key. */
case class LevelDef(code: String,
                    index: Int,
                    cutScore: Double,
                    minEvidenceCount: Int,
                    validityMonths: Option[Int])

/** A `{code, level}` pair as tagged on a course, a Learning Path or a question. */
case class CompetencyClaim(code: String, levelCode: String)

/** One edge of the requirement set: this position needs this competency at this level. */
case class RequirementDef(competencyId: String,
                          requiredLevel: String,
                          requiredLevelIndex: Int,
                          criticality: String)

/** Resolved, cached vocabulary of one competency framework. */
case class CompetencyMeta(frameworkId: String,
                          levels: List[LevelDef],
                          maxCompletionDerivedIndex: Int,
                          defaultRequiredLevelIndex: Int,
                          defaultRequiredLevel: String,
                          validityMonths: Map[String, Int],
                          claimsByNode: Map[String, List[CompetencyClaim]]) {

  def levelByCode(code: String): Option[LevelDef] =
    levels.find(l => l.code != null && l.code.equalsIgnoreCase(code))

  def levelIndexOf(code: String): Int = levelByCode(code).map(_.index).getOrElse(0)

  def claimsOf(nodeId: String): List[CompetencyClaim] = claimsByNode.getOrElse(nodeId, Nil)

  def isEmpty: Boolean = levels.isEmpty && claimsByNode.isEmpty
}

object CompetencyMeta {
  val empty: CompetencyMeta = CompetencyMeta("", Nil, 0, 0, "", Map.empty, Map.empty)
}

/** One immutable fact in the ledger. */
case class Evidence(userId: String,
                    competencyId: String,
                    evidenceId: String,
                    frameworkId: String,
                    level: String,
                    levelIndex: Int,
                    sourceType: String,
                    sourceId: String,
                    batchId: String,
                    score: Option[Double],
                    maxScore: Option[Double],
                    evidenceCount: Int,
                    issuerId: Option[String],
                    note: Option[String],
                    occurredOn: Long,
                    expiresOn: Option[Long],
                    revoked: Boolean = false,
                    revokedReason: Option[String] = None)

object SourceType {
  val COURSE = "COURSE"
  val ASSESSMENT = "ASSESSMENT"
  val LEARNING_PATH = "LEARNING_PATH"
  val EXTERNAL = "EXTERNAL"
  val all: Set[String] = Set(COURSE, ASSESSMENT, LEARNING_PATH, EXTERNAL)
}

/** Derived row in the passbook. Never written by anything except the projector. */
case class PassbookEntry(competencyId: String,
                         frameworkId: String,
                         level: String,
                         levelIndex: Int,
                         status: String,
                         sourceType: String,
                         governingEvidenceId: String,
                         attainedOn: Long,
                         expiresOn: Option[Long])

/** One row of a learner's gap against a position. */
case class GapRow(competencyId: String,
                  requiredLevel: String,
                  requiredLevelIndex: Int,
                  heldLevel: String,
                  heldLevelIndex: Int,
                  criticality: String,
                  status: String)

/** Learner's current and target positions. */
case class PositionAssignment(userId: String,
                              frameworkId: String,
                              currentPosition: Option[String],
                              targetPositions: Set[String],
                              source: String,
                              assignedOn: Long)

object Criticality {
  val MANDATORY = "MANDATORY"
  val DESIRABLE = "DESIRABLE"
}
