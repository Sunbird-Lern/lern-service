package org.sunbird.viewer.competency

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class SkillBadgeUtilSpec extends AnyFlatSpec with Matchers {

  private val ets = 1700000000000L
  private val mid = "fixed-mid"

  private val entry = SkillEntry(
    skillId = "dosage-calculation",
    frameworkId = "fw_health_competency",
    sourceType = SourceType.ASSESSMENT,
    governingEvidenceId = "0001700000000000000:123",
    attainedOn = ets)

  "issueEvent" should "carry the skill, its framework and the evidence that proved it" in {
    val json = SkillBadgeUtil.issueEvent("u1", entry, ets, mid)
    json should include("\"action\":\"issue-skill-badge\"")
    json should include("\"skillId\":\"dosage-calculation\"")
    json should include("\"frameworkId\":\"fw_health_competency\"")
    json should include("\"sourceType\":\"ASSESSMENT\"")
    json should include("\"evidenceId\":\"0001700000000000000:123\"")
    json should include("\"userIds\":[\"u1\"]")
  }

  it should "use the same BE_JOB_REQUEST envelope as the certificate instruction" in {
    val json = SkillBadgeUtil.issueEvent("u1", entry, ets, mid)
    json should include("\"eid\":\"BE_JOB_REQUEST\"")
    json should include("\"mid\":\"LP.1700000000000.fixed-mid\"")
    json should include("\"type\":\"SkillBadgeGeneration\"")
    json should include("\"id\":\"fw_health_competency_dosage-calculation\"")
  }

  it should "be byte-stable for the same inputs, so an event can be asserted on" in {
    SkillBadgeUtil.issueEvent("u1", entry, ets, mid) shouldBe
      SkillBadgeUtil.issueEvent("u1", entry, ets, mid)
  }

  it should "produce valid JSON, not a hand-rolled string" in {
    val json = SkillBadgeUtil.issueEvent("u1", entry, ets, mid)
    val parsed = new com.fasterxml.jackson.databind.ObjectMapper()
      .readValue(json, classOf[java.util.Map[String, AnyRef]])
    parsed.get("eid") shouldBe "BE_JOB_REQUEST"
  }

  it should "escape a value that would otherwise break the payload" in {
    val awkward = entry.copy(skillId = """quote"and\backslash""")
    val json = SkillBadgeUtil.issueEvent("u1", awkward, ets, mid)
    val parsed = new com.fasterxml.jackson.databind.ObjectMapper()
      .readValue(json, classOf[java.util.Map[String, AnyRef]])
    val edata = parsed.get("edata").asInstanceOf[java.util.Map[String, AnyRef]]
    edata.get("skillId") shouldBe """quote"and\backslash"""
  }

  "revokeEvent" should "name the skill and the revoke action" in {
    val json = SkillBadgeUtil.revokeEvent("u1", "ppe-use", "fw_health_competency", ets, mid)
    json should include("\"action\":\"revoke-skill-badge\"")
    json should include("\"skillId\":\"ppe-use\"")
    json should include("\"trigger\":\"auto-revoke\"")
  }

  it should "not claim evidence, since a revoked skill has none standing" in {
    SkillBadgeUtil.revokeEvent("u1", "ppe-use", "fw", ets, mid) should not include "evidenceId"
  }

  "the topic key" should "be the one deployments configure" in {
    SkillBadgeUtil.TOPIC_KEY shouldBe "kafka_topics_skill_badge_instruction"
  }
}
