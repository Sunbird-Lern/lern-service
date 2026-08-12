package org.sunbird.viewer.util

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.sunbird.activity.util.{LpPolicyUtil, NodeMeta}

class LpPolicyUtilParseSpec extends AnyFlatSpec with Matchers {

  "parseFrameworkCategoryCode" should "return the highest-index category code" in {
    val json = """{"result":{"framework":{"identifier":"USF","categories":[
      {"code":"board","index":1},{"code":"subject","index":2},{"code":"skill","index":3}]}}}"""
    LpPolicyUtil.parseFrameworkCategoryCode(json) shouldBe Some("skill")
  }

  it should "return None when there are no categories" in {
    LpPolicyUtil.parseFrameworkCategoryCode("""{"result":{"framework":{"categories":[]}}}""") shouldBe None
  }

  "parseLpNodes" should "map each node to its primaryCategory, <categoryCode> terms and childNodes" in {
    val json = """{"result":{"count":2,"Content":[
      {"identifier":"c1","primaryCategory":"Course","skill":["Python Programming"],"childNodes":["q1"]},
      {"identifier":"c2","primaryCategory":"Practice Question Set","childNodes":[]}]}}"""
    val nodes = LpPolicyUtil.parseLpNodes(json, "skill")
    nodes("c1") shouldBe NodeMeta("Course", Set("Python Programming"), List("q1"))
    nodes("c2").primaryCategory shouldBe "Practice Question Set"
    nodes("c2").skills shouldBe empty
  }

  it should "read whichever objectType array key is present (Question), not just content" in {
    val json = """{"result":{"count":1,"Question":[
      {"identifier":"q1","primaryCategory":"Practice Question Set","skill":["JavaScript"]}]}}"""
    LpPolicyUtil.parseLpNodes(json, "skill")("q1").skills shouldBe Set("JavaScript")
  }

  "parseField" should "read a scalar field for an identifier" in {
    val json = """{"result":{"content":[{"identifier":"root","policy":"PriorLearning","framework":"USF"}]}}"""
    LpPolicyUtil.parseField(json, "root", "policy") shouldBe Some("PriorLearning")
    LpPolicyUtil.parseField(json, "root", "framework") shouldBe Some("USF")
  }
}
