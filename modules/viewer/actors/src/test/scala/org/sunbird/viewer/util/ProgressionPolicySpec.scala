package org.sunbird.viewer.util

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class ProgressionPolicySpec extends AnyFlatSpec with Matchers {

  // Reference tree: L1[CRS-A]  L2[CRS-B,CRS-C]  L3[CRS-D,CRS-E]  L4[CRS-F]  (ancestors nearest-first)
  private val anc: Map[String, List[String]] = Map(
    "CRS-A" -> List("L1", "do_lp"),
    "CRS-B" -> List("L2", "do_lp"), "CRS-C" -> List("L2", "do_lp"),
    "CRS-D" -> List("L3", "do_lp"), "CRS-E" -> List("L3", "do_lp"),
    "CRS-F" -> List("L4", "do_lp"),
    "CRS-X" -> List("grp", "L2", "do_lp")) // nested one level deeper inside L2
  private val ancestorsOf: String => List[String] = anc.getOrElse(_, Nil)
  private val order = List("CRS-A", "CRS-B", "CRS-C", "CRS-D", "CRS-E", "CRS-F")

  "levelOf" should "return the level node (the ancestor that is the direct child of the root)" in {
    ProgressionPolicy.levelOf("CRS-C", ancestorsOf, "do_lp") shouldBe Some("L2")
  }

  it should "pick the direct child of root even when the course is nested deeper (not the nearest ancestor)" in {
    ProgressionPolicy.levelOf("CRS-X", ancestorsOf, "do_lp") shouldBe Some("L2")
  }

  // Hybrid: levels are derived from a LEAF's ancestors (root LAST), which also contain the unit + course.
  // levelOf must still pick the level = last non-root, regardless of the extra leading nodes.
  it should "pick the level from a full leaf ancestor chain [unit, course, level, root]" in {
    val leafChain: String => List[String] = _ => List("U1", "CRS-B", "L2", "do_lp")
    ProgressionPolicy.levelOf("CRS-B", leafChain, "do_lp") shouldBe Some("L2")
  }

  it should "treat a course directly under root (no level wrapper) as its own level" in {
    val flatChain: String => List[String] = _ => List("U1", "CRS-Z", "do_lp")
    ProgressionPolicy.levelOf("CRS-Z", flatChain, "do_lp") shouldBe Some("CRS-Z")
  }

  "coursesOfLevel" should "group the level's courses in trackablenodes order" in {
    ProgressionPolicy.coursesOfLevel("L2", order, ancestorsOf, "do_lp") shouldBe List("CRS-B", "CRS-C")
  }

  "orderedLevels" should "list levels in first-appearance order" in {
    ProgressionPolicy.orderedLevels(order, ancestorsOf, "do_lp") shouldBe List("L1", "L2", "L3", "L4")
  }

  "levelByCourse (precomputed map)" should "match the ancestorsOf-based overloads" in {
    val m = ProgressionPolicy.levelByCourse(order, ancestorsOf, "do_lp")
    m shouldBe Map("CRS-A" -> "L1", "CRS-B" -> "L2", "CRS-C" -> "L2",
      "CRS-D" -> "L3", "CRS-E" -> "L3", "CRS-F" -> "L4")
    ProgressionPolicy.orderedLevels(order, m) shouldBe ProgressionPolicy.orderedLevels(order, ancestorsOf, "do_lp")
    ProgressionPolicy.coursesOfLevel("L2", order, m) shouldBe
      ProgressionPolicy.coursesOfLevel("L2", order, ancestorsOf, "do_lp")
  }

  "computeOptionalNodes" should "waive a course whose competencies are all held at the required level" in {
    val opt = ProgressionPolicy.computeOptionalNodes(
      policy = "Adaptive",
      courses = List("CRS-B", "CRS-C"),
      claimsByCourse = Map("CRS-B" -> List("c1" -> 3), "CRS-C" -> List("c2" -> 3)),
      assessmentCourses = Set("CRS-C"),
      heldLevels = Map("c1" -> 3, "c2" -> 4))
    opt shouldBe Set("CRS-B") // CRS-C is an assessment -> never optional
  }

  it should "not waive a course held below the claimed level" in {
    ProgressionPolicy.computeOptionalNodes(
      policy = "Adaptive",
      courses = List("CRS-B"),
      claimsByCourse = Map("CRS-B" -> List("c1" -> 3)),
      assessmentCourses = Set.empty,
      heldLevels = Map("c1" -> 2)) shouldBe empty
  }

  it should "waive when the level held exceeds the level claimed" in {
    ProgressionPolicy.computeOptionalNodes(
      policy = "Adaptive",
      courses = List("CRS-B"),
      claimsByCourse = Map("CRS-B" -> List("c1" -> 2)),
      assessmentCourses = Set.empty,
      heldLevels = Map("c1" -> 4)) shouldBe Set("CRS-B")
  }

  it should "require every claimed competency, not just one" in {
    ProgressionPolicy.computeOptionalNodes(
      policy = "Adaptive",
      courses = List("CRS-B"),
      claimsByCourse = Map("CRS-B" -> List("c1" -> 2, "c2" -> 2)),
      assessmentCourses = Set.empty,
      heldLevels = Map("c1" -> 4)) shouldBe empty
  }

  it should "not waive an untagged course on competency grounds" in {
    ProgressionPolicy.computeOptionalNodes(
      policy = "Adaptive",
      courses = List("CRS-B"),
      claimsByCourse = Map.empty,
      assessmentCourses = Set.empty,
      heldLevels = Map("c1" -> 4)) shouldBe empty
  }

  it should "waive nothing under Strict" in {
    ProgressionPolicy.computeOptionalNodes("Strict", List("CRS-B"),
      Map("CRS-B" -> List("c1" -> 1)), Set.empty, Map("c1" -> 4)) shouldBe empty
  }

  it should "waive a prior-completed course regardless of competencies (PriorLearning)" in {
    val opt = ProgressionPolicy.computeOptionalNodes(
      policy = "PriorLearning",
      courses = List("CRS-B", "CRS-C"),
      claimsByCourse = Map.empty,
      assessmentCourses = Set.empty,
      heldLevels = Map.empty,
      priorCompleted = Set("CRS-B"))
    opt shouldBe Set("CRS-B")
  }

  it should "never waive an assessment course even if prior-completed" in {
    val opt = ProgressionPolicy.computeOptionalNodes(
      policy = "PriorLearning",
      courses = List("CRS-B"),
      claimsByCourse = Map.empty,
      assessmentCourses = Set("CRS-B"),
      heldLevels = Map.empty,
      priorCompleted = Set("CRS-B"))
    opt shouldBe empty
  }
}
