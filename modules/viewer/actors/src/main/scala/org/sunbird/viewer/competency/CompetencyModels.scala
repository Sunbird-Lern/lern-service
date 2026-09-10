package org.sunbird.viewer.competency

/**
 * Resolved, cached vocabulary of one competency framework.
 *
 * The framework is one `competency` category whose terms nest by `children`, plus an optional
 * `role` category. Only a term with no children is taggable, so `leaves` is what every lookup is
 * checked against. `roleSkills` maps a role code to the leaf skills it requires.
 *
 * `tierLabels` are display names for the tree's depths, outermost first. They name nothing the
 * engine reads; depth comes from the tree, not from the labels.
 */
case class CompetencyMeta(frameworkId: String,
                          tierLabels: List[String],
                          leaves: Set[String],
                          depth: Int,
                          roleSkills: Map[String, Set[String]],
                          claimsByNode: Map[String, List[String]]) {

  def isLeaf(code: String): Boolean = code != null && leaves.contains(code)

  /** Leaf skills one role requires. Empty when the role is unknown or declares none. */
  def skillsOf(roleId: String): Set[String] =
    if (roleId == null) Set.empty else roleSkills.getOrElse(roleId, Set.empty)

  def claimsOf(nodeId: String): List[String] = claimsByNode.getOrElse(nodeId, Nil)

  /**
   * A framework that resolved no leaves cannot credit anything, so callers treat it as
   * unresolvable and surface it rather than crediting silently.
   *
   * Deliberately not "declares no roles": roles are optional, the tree is not.
   */
  def isEmpty: Boolean = leaves.isEmpty
}

object CompetencyMeta {
  val empty: CompetencyMeta = CompetencyMeta("", Nil, Set.empty, 0, Map.empty, Map.empty)
}

/**
 * One immutable fact in the ledger.
 *
 * A row exists only for a skill the learner earned. Nothing is appended for an attempted but
 * unearned skill, because the profile reads held as "a live row exists".
 */
case class Evidence(userId: String,
                    skillId: String,
                    evidenceId: String,
                    frameworkId: String,
                    sourceType: String,
                    sourceId: String,
                    batchId: String,
                    score: Option[Double],
                    maxScore: Option[Double],
                    issuerId: Option[String],
                    note: Option[String],
                    occurredOn: Long,
                    revoked: Boolean = false,
                    revokedReason: Option[String] = None)

object SourceType {
  val COURSE = "COURSE"
  val ASSESSMENT = "ASSESSMENT"
  val LEARNING_PATH = "LEARNING_PATH"
  val EXTERNAL = "EXTERNAL"
  val all: Set[String] = Set(COURSE, ASSESSMENT, LEARNING_PATH, EXTERNAL)
}

/**
 * Derived row in the skill profile. Written by the projector and nothing else.
 *
 * There is no status field: held is the only state, and not held is the absence of a row.
 */
case class SkillEntry(skillId: String,
                      frameworkId: String,
                      sourceType: String,
                      governingEvidenceId: String,
                      attainedOn: Long)

/** One row of a learner's gap against a role. */
case class GapRow(skillId: String, status: String)

/** Learner's current and target roles. */
case class RoleAssignment(userId: String,
                          frameworkId: String,
                          currentRole: Option[String],
                          targetRoles: Set[String],
                          source: String,
                          assignedOn: Long)

object RoleSource {
  val HRMS = "HRMS"
  val PROFILE = "PROFILE"
  val SELF = "SELF"
}
