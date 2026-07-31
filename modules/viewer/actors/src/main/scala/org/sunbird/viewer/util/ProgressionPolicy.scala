package org.sunbird.viewer.util

import scala.jdk.CollectionConverters._

/**
 * Pure, host-agnostic decisions for Learning-Path progression. No I/O — every input is passed in,
 * so these are trivially unit-testable and run identically in-request or in the async aggregator.
 * (Structural helpers only for now; level/optionality helpers are added in a later slice.)
 */
object ProgressionPolicy {

  private val ASSESSMENT_CATEGORY = "practice question set"

  private def isTrackable(node: java.util.Map[String, AnyRef]): Boolean =
    node.get("trackable") match {
      case t: java.util.Map[_, _] =>
        "Yes".equalsIgnoreCase(String.valueOf(t.asInstanceOf[java.util.Map[String, AnyRef]].get("enabled")))
      case _ => false
    }

  private def childrenOf(node: java.util.Map[String, AnyRef]): List[java.util.Map[String, AnyRef]] =
    node.get("children") match {
      case l: java.util.List[_] => l.asInstanceOf[java.util.List[java.util.Map[String, AnyRef]]].asScala.toList
      case _ => Nil
    }

  /**
   * Structural LP detection: true iff the collection has a descendant that is itself a trackable
   * collection (`trackable.enabled == "Yes"`). A plain course — whose children are non-trackable
   * content — is false. No reliance on `policy`/`primaryCategory`.
   */
  def hasNestedTrackable(rootNode: java.util.Map[String, AnyRef]): Boolean = {
    def hasTrackableDescendant(node: java.util.Map[String, AnyRef]): Boolean =
      childrenOf(node).exists(c => isTrackable(c) || hasTrackableDescendant(c))
    hasTrackableDescendant(rootNode)
  }

  /** A course is an assessment course iff it has a child with `primaryCategory == "Practice Question Set"`. */
  def isAssessment(courseNode: java.util.Map[String, AnyRef]): Boolean =
    childrenOf(courseNode).exists(c =>
      ASSESSMENT_CATEGORY.equalsIgnoreCase(String.valueOf(c.get("primaryCategory"))))

  // ── Level helpers + optionality resolver (pure; take their data as parameters, no I/O) ──

  /**
   * The course's level = the ancestor that is a **direct child of the root** (top-most under root),
   * i.e. `lastOption` of the ancestor chain excluding the root — NOT the nearest ancestor. For a
   * 2-deep root->level->course tree they coincide; only this definition is correct if a course is
   * nested deeper inside a level. `ancestorsOf` returns the chain nearest-first.
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

  /**
   * A course is optional iff it is not an assessment and all of its (non-empty) skills are achieved.
   * `Strict` waives nothing. `Adaptive`/`PriorLearning` differ only in how `skillsAchieved` is built
   * by the caller — this function is policy-agnostic beyond the `Strict` short-circuit.
   */
  def computeOptionalNodes(policy: String, courses: List[String],
                           skillsByCourse: Map[String, Set[String]],
                           assessmentCourses: Set[String],
                           skillsAchieved: Set[String]): Set[String] = {
    if ("Strict".equalsIgnoreCase(policy)) Set.empty
    else courses.filter { c =>
      val skills = skillsByCourse.getOrElse(c, Set.empty)
      !assessmentCourses.contains(c) && skills.nonEmpty && skills.subsetOf(skillsAchieved)
    }.toSet
  }

  /**
   * A skill is achieved iff **all** of its tagged questions are correct. Pure core of `skillsFrom`
   * (the aggregator supplies `skillToQuestions` from `/v3/search` tags and `correctQuestions` from
   * `assessment_aggregator`). Skills with no tagged questions are never achieved.
   */
  def computeAchievedSkills(skillToQuestions: Map[String, Set[String]],
                            correctQuestions: Set[String]): Set[String] =
    skillToQuestions.collect {
      case (skill, qs) if qs.nonEmpty && qs.subsetOf(correctQuestions) => skill
    }.toSet
}
