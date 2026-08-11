package org.sunbird.viewer.util

/**
 * Pure, host-agnostic decisions for Learning-Path progression. No I/O — every input is passed in,
 * so these are trivially unit-testable and run identically in-request or in the async aggregator.
 */
object ProgressionPolicy {

  /**
   * The course's level = the ancestor that is a direct child of the root (top-most under root),
   * i.e. lastOption of the ancestor chain excluding the root — NOT the nearest ancestor. `ancestorsOf`
   * returns the chain nearest-first.
   */
  def levelOf(course: String, ancestorsOf: String => List[String], root: String): Option[String] =
    ancestorsOf(course).filterNot(_ == root).lastOption

  /** Trackable courses whose level is `level`, preserving `trackable` (trackablenodes) order. */
  def coursesOfLevel(level: String, trackable: List[String],
                     ancestorsOf: String => List[String], root: String): List[String] =
    trackable.filter(c => levelOf(c, ancestorsOf, root).contains(level))

  /** Distinct level nodes in first-appearance order along `trackable`. */
  def orderedLevels(trackable: List[String],
                    ancestorsOf: String => List[String], root: String): List[String] =
    trackable.flatMap(c => levelOf(c, ancestorsOf, root)).distinct

  /** Precompute each course's level once so downstream lookups don't re-invoke ancestorsOf per call. */
  def levelByCourse(trackable: List[String], ancestorsOf: String => List[String], root: String): Map[String, String] =
    trackable.flatMap(c => levelOf(c, ancestorsOf, root).map(c -> _)).toMap

  def orderedLevels(trackable: List[String], levelByCourse: Map[String, String]): List[String] =
    trackable.flatMap(levelByCourse.get).distinct

  def coursesOfLevel(level: String, trackable: List[String], levelByCourse: Map[String, String]): List[String] =
    trackable.filter(c => levelByCourse.get(c).contains(level))

  /**
   * A course is optional iff it is not an assessment and all of its (non-empty) skills are achieved.
   * `Strict` waives nothing; other policies differ only in how the caller builds `skillsAchieved`.
   */
  def computeOptionalNodes(policy: String, courses: List[String],
                           skillsByCourse: Map[String, Set[String]],
                           assessmentCourses: Set[String],
                           skillsAchieved: Set[String],
                           priorCompleted: Set[String] = Set.empty): Set[String] = {
    if ("Strict".equalsIgnoreCase(policy)) Set.empty
    else courses.filter { c =>
      !assessmentCourses.contains(c) && {
        val skills = skillsByCourse.getOrElse(c, Set.empty)
        priorCompleted.contains(c) || (skills.nonEmpty && skills.subsetOf(skillsAchieved))
      }
    }.toSet
  }
}
