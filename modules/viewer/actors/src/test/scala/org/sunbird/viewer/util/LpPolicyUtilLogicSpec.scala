package org.sunbird.viewer.util

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.sunbird.activity.util.{LpPolicyUtil, NodeMeta}

class LpPolicyUtilLogicSpec extends AnyFlatSpec with Matchers {
  private val nodes = Map(
    "crsA" -> NodeMeta("Course", Set("Python Programming"), List("qsA")),
    "qsA"  -> NodeMeta("Practice Question Set", Set.empty, Nil),
    "crsB" -> NodeMeta("Course", Set("JavaScript"), Nil))

  "isAssessment" should "be true when a child is a Practice Question Set" in {
    LpPolicyUtil.isAssessment("crsA", nodes) shouldBe true
    LpPolicyUtil.isAssessment("crsB", nodes) shouldBe false
  }

  "questionSets" should "list the Practice-Question-Set children" in {
    LpPolicyUtil.questionSets("crsA", nodes) shouldBe List("qsA")
    LpPolicyUtil.questionSets("crsB", nodes) shouldBe Nil
  }
}
