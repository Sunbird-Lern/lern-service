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
