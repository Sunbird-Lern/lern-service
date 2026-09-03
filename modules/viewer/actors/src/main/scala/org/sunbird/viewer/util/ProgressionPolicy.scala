package org.sunbird.viewer.util

object ProgressionPolicy {

  def levelOf(course: String, ancestorsOf: String => List[String], root: String): Option[String] =
    ancestorsOf(course).filterNot(_ == root).lastOption

  def coursesOfLevel(level: String, trackable: List[String],
                     ancestorsOf: String => List[String], root: String): List[String] =
    trackable.filter(c => levelOf(c, ancestorsOf, root).contains(level))

  def orderedLevels(trackable: List[String],
                    ancestorsOf: String => List[String], root: String): List[String] =
    trackable.flatMap(c => levelOf(c, ancestorsOf, root)).distinct

  def levelByCourse(trackable: List[String], ancestorsOf: String => List[String], root: String): Map[String, String] =
    trackable.flatMap(c => levelOf(c, ancestorsOf, root).map(c -> _)).toMap

  def orderedLevels(trackable: List[String], levelByCourse: Map[String, String]): List[String] =
    trackable.flatMap(levelByCourse.get).distinct

  def coursesOfLevel(level: String, trackable: List[String], levelByCourse: Map[String, String]): List[String] =
    trackable.filter(c => levelByCourse.get(c).contains(level))

  /**
   * Which courses this learner may skip.
   *
   * A course is waived when every competency it claims is already held at or above the claimed
   * level, or (PriorLearning) when the learner has completed it before. Assessment courses are
   * never waived: the evidence itself cannot be skipped.
   *
   * `claimsByCourse` maps a course to its `{competency, levelIndex}` claims; `heldLevels` maps a
   * competency to the level index in the learner's passbook. A course with no claims cannot be
   * waived on competency grounds, only on prior completion.
   */
  def computeOptionalNodes(policy: String,
                           courses: List[String],
                           claimsByCourse: Map[String, List[(String, Int)]],
                           assessmentCourses: Set[String],
                           heldLevels: Map[String, Int],
                           priorCompleted: Set[String] = Set.empty): Set[String] = {
    if ("Strict".equalsIgnoreCase(policy)) Set.empty
    else courses.filter { c =>
      !assessmentCourses.contains(c) && {
        val claims = claimsByCourse.getOrElse(c, Nil)
        priorCompleted.contains(c) ||
          (claims.nonEmpty && claims.forall { case (comp, required) =>
            heldLevels.getOrElse(comp, 0) >= required && required > 0
          })
      }
    }.toSet
  }
}
