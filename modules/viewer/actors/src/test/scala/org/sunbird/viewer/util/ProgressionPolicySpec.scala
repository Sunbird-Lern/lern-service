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

  "coursesOfLevel" should "group the level's courses in trackablenodes order" in {
    ProgressionPolicy.coursesOfLevel("L2", order, ancestorsOf, "do_lp") shouldBe List("CRS-B", "CRS-C")
  }

  "orderedLevels" should "list levels in first-appearance order" in {
    ProgressionPolicy.orderedLevels(order, ancestorsOf, "do_lp") shouldBe List("L1", "L2", "L3", "L4")
  }

  "computeOptionalNodes" should "waive a fully-known non-assessment course but never an assessment" in {
    val opt = ProgressionPolicy.computeOptionalNodes(
      policy = "Adaptive",
      courses = List("CRS-B", "CRS-C"),
      skillsByCourse = Map("CRS-B" -> Set("s1"), "CRS-C" -> Set("s2")),
      assessmentCourses = Set("CRS-C"),
      skillsAchieved = Set("s1", "s2"))
    opt shouldBe Set("CRS-B") // CRS-C is an assessment -> never optional
  }

  it should "waive nothing under Strict" in {
    ProgressionPolicy.computeOptionalNodes("Strict", List("CRS-B"),
      Map("CRS-B" -> Set("s1")), Set.empty, Set("s1")) shouldBe empty
  }
}
