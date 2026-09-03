package org.sunbird.viewer.util

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.sunbird.viewer.util.{LpPolicyUtil, NodeMeta}

class LpPolicyUtilParseSpec extends AnyFlatSpec with Matchers {

  "parseLpNodes" should "map each node to its primaryCategory and childNodes" in {
    val json = """{"result":{"count":2,"Content":[
      {"identifier":"c1","primaryCategory":"Course","childNodes":["q1"]},
      {"identifier":"c2","primaryCategory":"Practice Question Set","childNodes":[]}]}}"""
    val nodes = LpPolicyUtil.parseLpNodes(json)
    nodes("c1") shouldBe NodeMeta("Course", List("q1"))
    nodes("c2").primaryCategory shouldBe "Practice Question Set"
    nodes("c2").childNodes shouldBe empty
  }

  it should "read whichever objectType array key is present (Question), not just content" in {
    val json = """{"result":{"count":1,"Question":[
      {"identifier":"q1","primaryCategory":"Practice Question Set"}]}}"""
    LpPolicyUtil.parseLpNodes(json)("q1").primaryCategory shouldBe "Practice Question Set"
  }

  it should "default a missing primaryCategory to empty rather than failing" in {
    LpPolicyUtil.parseLpNodes("""{"result":{"content":[{"identifier":"c1"}]}}""")("c1") shouldBe
      NodeMeta("", Nil)
  }

  "parseField" should "read a scalar field for an identifier" in {
    val json = """{"result":{"content":[{"identifier":"root","policy":"PriorLearning","competencyFramework":"fw_health"}]}}"""
    LpPolicyUtil.parseField(json, "root", "policy") shouldBe Some("PriorLearning")
    LpPolicyUtil.parseField(json, "root", "competencyFramework") shouldBe Some("fw_health")
  }

  it should "return None for a blank or absent field" in {
    val json = """{"result":{"content":[{"identifier":"root","competencyFramework":""}]}}"""
    LpPolicyUtil.parseField(json, "root", "competencyFramework") shouldBe None
    LpPolicyUtil.parseField(json, "root", "policy") shouldBe None
    LpPolicyUtil.parseField(json, "missing", "policy") shouldBe None
  }
}
