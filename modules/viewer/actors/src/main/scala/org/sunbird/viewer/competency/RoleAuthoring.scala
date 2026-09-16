package org.sunbird.viewer.competency

/**
 * Pure rules for authoring the role -> skill map.
 *
 * Kept free of Cassandra so the part that decides what changes can be tested directly. The service
 * does the reading and writing; this decides what those writes should be.
 */
object RoleAuthoring {

  /**
   * What applying `authored` to a role currently holding `current` would change.
   *
   * REPLACE, NOT MERGE. `authored` is the full requirement set, so a skill present today and absent
   * from it is a removal. Merging instead would make un-ticking a box in the authoring matrix a
   * no-op: roles could only ever grow, and would drift silently from the spreadsheet they came from.
   *
   * Requirements naming something that is not a leaf are reported as `rejected` rather than
   * applied. An interior term is never tagged on content, so no evidence path exists and the skill
   * can never be held - crediting it would report a learner ready for a role they cannot finish.
   * A code absent from the tree entirely (a typo, or a term retired out of it) lands here too.
   */
  def diff(roleId: String, authored: Set[String], current: Set[String],
           leaves: Set[String]): RoleDiff = {
    val (valid, rejected) = authored.partition(leaves.contains)
    RoleDiff(
      roleId = roleId,
      added = valid.diff(current),
      removed = current.diff(valid),
      unchanged = valid.intersect(current),
      rejected = rejected)
  }

  /**
   * The version a role should carry after applying `d`.
   *
   * Bumped only on a real change, so re-applying an unchanged matrix is a no-op and an in-flight
   * learning path pinned to a version is not invalidated by a no-op import.
   */
  def nextVersion(currentVersion: Int, d: RoleDiff): Int =
    if (d.added.nonEmpty || d.removed.nonEmpty) currentVersion + 1 else math.max(currentVersion, 1)
}
