package org.sunbird.viewer.util

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.sunbird.viewer.util.{LpPolicyUtil, NodeMeta}

class LpPolicyUtilLogicSpec extends AnyFlatSpec with Matchers {
  private val nodes = Map(
    // assessment step = a "Evaluation Course" collection wrapping a PQS
    "assess" -> NodeMeta("Evaluation Course", Set.empty, List("qsA")),
    "qsA"    -> NodeMeta("Practice Question Set", Set.empty, Nil),
    // a normal content course that merely embeds a self-check quiz — NOT an assessment
    "crsA"   -> NodeMeta("Course", Set("Python Programming"), List("qsA")),
    "crsB"   -> NodeMeta("Course", Set("JavaScript"), Nil))

  "isAssessment" should "be true only for the Course-Assessment category, not for a course embedding a PQS" in {
    LpPolicyUtil.isAssessment("assess", nodes) shouldBe true
    LpPolicyUtil.isAssessment("crsA", nodes) shouldBe false // false-positive killed: content course with a quiz
    LpPolicyUtil.isAssessment("crsB", nodes) shouldBe false
  }

  "questionSets" should "list the Practice-Question-Set children of the assessment wrapper" in {
    LpPolicyUtil.questionSets("assess", nodes) shouldBe List("qsA")
    LpPolicyUtil.questionSets("crsB", nodes) shouldBe Nil
  }
}
