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
   * A course is waived when every leaf skill it teaches is already held, or (PriorLearning) when
   * the learner has completed it before. Assessment courses are never waived: the evidence itself
   * cannot be skipped.
   *
   * `skillsByCourse` maps a course to the leaf skills it teaches; `held` is what the learner's
   * profile holds. A course with no skills cannot be waived on skill grounds, only on prior
   * completion.
   *
   * Leaf granularity makes this stricter than a coarser tag would: a course teaching three skills
   * is waived only when all three are held, so a learner who missed one still takes it.
   */
  def computeOptionalNodes(policy: String,
                           courses: List[String],
                           skillsByCourse: Map[String, List[String]],
                           assessmentCourses: Set[String],
                           held: Set[String],
                           priorCompleted: Set[String] = Set.empty): Set[String] = {
    if ("Strict".equalsIgnoreCase(policy)) Set.empty
    else courses.filter { c =>
      !assessmentCourses.contains(c) && {
        val skills = skillsByCourse.getOrElse(c, Nil)
        priorCompleted.contains(c) || (skills.nonEmpty && skills.forall(held.contains))
      }
    }.toSet
  }
}
