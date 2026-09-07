package org.sunbird.viewer.competency

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class CompetencyFrameworkUtilParseSpec extends AnyFlatSpec with Matchers {

  import CompetencyFrameworkUtil._

  // full variant: requirement terms carry the edges. Sparse level indices on purpose.
  private val fullFramework =
    """{"result":{"framework":{"identifier":"fw_health","type":"Competency",
       "defaultRequiredLevel":"proficient","maxCompletionDerivedLevel":"progressing",
       "categories":[
         {"code":"proficiencylevel","terms":[
            {"code":"beginning","index":10,"cutScore":40,"minEvidenceCount":4},
            {"code":"progressing","index":20,"cutScore":55,"minEvidenceCount":5},
            {"code":"proficient","index":30,"cutScore":70,"minEvidenceCount":6},
            {"code":"advanced","index":40,"cutScore":85,"minEvidenceCount":8,"validityMonths":24}]},
         {"code":"competency","terms":[
            {"code":"medication","validityMonths":24},
            {"code":"comms"}]},
         {"code":"position","terms":[{"code":"nurse"},{"code":"officer"}]},
         {"code":"competencyrequirement","terms":[
            {"code":"r1","criticality":"MANDATORY","associations":[
               {"category":"position","code":"nurse"},
               {"category":"competency","code":"medication"},
               {"category":"proficiencylevel","code":"proficient"}]},
            {"code":"r2","criticality":"DESIRABLE","associations":[
               {"category":"position","code":"nurse"},
               {"category":"competency","code":"comms"},
               {"category":"proficiencylevel","code":"progressing"}]},
            {"code":"r3","associations":[
               {"category":"position","code":"officer"},
               {"category":"competency","code":"medication"},
               {"category":"proficiencylevel","code":"advanced"}]}]}]}}}"""

  // small variant: no requirement terms, positions associate straight to competencies
  private val smallFramework =
    """{"result":{"framework":{"identifier":"fw_ncf","defaultRequiredLevel":"proficient",
       "categories":[
         {"code":"proficiencylevel","terms":[
            {"code":"progressing","index":20,"cutScore":55,"minEvidenceCount":5},
            {"code":"proficient","index":30,"cutScore":70,"minEvidenceCount":6}]},
         {"code":"position","terms":[
            {"code":"grade-6","associations":[
               {"category":"competency","code":"no-integers"},
               {"category":"competency","code":"no-fractions"}]}]}]}}}"""

  private def indexOf(json: String): String => Int = {
    val levels = parseLevels(json)
    code => levels.find(_.code.equalsIgnoreCase(code)).map(_.index).getOrElse(0)
  }

  "parseLevels" should "read the scale ordered by index, preserving sparse values" in {
    val levels = parseLevels(fullFramework)
    levels.map(_.code) shouldBe List("beginning", "progressing", "proficient", "advanced")
    levels.map(_.index) shouldBe List(10, 20, 30, 40)
    levels.find(_.code == "proficient").get.cutScore shouldBe 70d
    levels.find(_.code == "proficient").get.minEvidenceCount shouldBe 6
    levels.find(_.code == "advanced").get.validityMonths shouldBe Some(24)
    levels.find(_.code == "beginning").get.validityMonths shouldBe None
  }

  it should "be empty for a framework that does not resolve" in {
    parseLevels("{}") shouldBe empty
    parseLevels("""{"result":{"framework":{"categories":[]}}}""") shouldBe empty
    parseLevels("not json at all") shouldBe empty
  }

  "frameworkField" should "read the framework-level settings" in {
    frameworkField(fullFramework, "defaultRequiredLevel") shouldBe Some("proficient")
    frameworkField(fullFramework, "maxCompletionDerivedLevel") shouldBe Some("progressing")
    frameworkField(fullFramework, "nosuchfield") shouldBe None
  }

  "parseValidity" should "read per-competency overrides only where set" in {
    parseValidity(fullFramework) shouldBe Map("medication" -> 24)
  }

  "parseRequirements" should "build one edge per requirement term, grouped by position" in {
    val reqs = parseRequirements(fullFramework, "proficient", 30, indexOf(fullFramework))
    reqs.keySet shouldBe Set("nurse", "officer")
    val nurse = reqs("nurse").map(r => r.competencyId -> r).toMap
    nurse("medication").requiredLevelIndex shouldBe 30
    nurse("medication").criticality shouldBe Criticality.MANDATORY
    nurse("comms").requiredLevelIndex shouldBe 20
    nurse("comms").criticality shouldBe Criticality.DESIRABLE
    // the same competency, required at a different level by a different position
    reqs("officer").head.competencyId shouldBe "medication"
    reqs("officer").head.requiredLevelIndex shouldBe 40
  }

  it should "default a missing criticality to mandatory" in {
    parseRequirements(fullFramework, "proficient", 30, indexOf(fullFramework))("officer")
      .head.criticality shouldBe Criticality.MANDATORY
  }

  it should "fall back to the small variant when there are no requirement terms" in {
    val reqs = parseRequirements(smallFramework, "proficient", 30, indexOf(smallFramework))
    reqs.keySet shouldBe Set("grade-6")
    reqs("grade-6").map(_.competencyId).toSet shouldBe Set("no-integers", "no-fractions")
    // every outcome required at the framework default, uniformly
    reqs("grade-6").map(_.requiredLevelIndex).distinct shouldBe List(30)
    reqs("grade-6").map(_.criticality).distinct shouldBe List(Criticality.MANDATORY)
  }

  it should "be empty when neither variant is present" in {
    parseRequirements("""{"result":{"framework":{"categories":[]}}}""", "x", 1, _ => 1) shouldBe empty
  }

  // The authoring sheets renamed this category to `requirement`; frameworks created before the
  // rename still say `competencyrequirement`. Reading only the old spelling made a framework built
  // from the current sheets resolve zero requirements -- which readiness reports as 100% ready.
  it should "read the requirement category under either spelling" in {
    val renamed = fullFramework.replace("\"competencyrequirement\"", "\"requirement\"")
    parseRequirements(renamed, "proficient", 30, indexOf(renamed)) shouldBe
      parseRequirements(fullFramework, "proficient", 30, indexOf(fullFramework))
  }

  "associationsByCategory" should "group a term's associations and tolerate identifier-only links" in {
    val terms = termsOf(fullFramework, CAT_REQUIREMENT)
    val assoc = associationsByCategory(terms.head)
    assoc("position") shouldBe List("nurse")
    assoc("competency") shouldBe List("medication")
    assoc("proficiencylevel") shouldBe List("proficient")
  }

  "parseClaims" should "read level-paired competencies off a search row" in {
    val json = """{"result":{"count":2,"Content":[
      {"identifier":"crs1","competencies":[{"code":"medication","level":"l3"},{"code":"comms","level":"l2"}]},
      {"identifier":"crs2","competencies":[]}]}}"""
    val claims = parseClaims(json)
    claims("crs1") should contain theSameElementsAs List(
      CompetencyClaim("medication", "l3"), CompetencyClaim("comms", "l2"))
    claims should not contain key("crs2") // untagged rows are dropped
  }

  it should "read whichever objectType key the search returns" in {
    val json = """{"result":{"count":1,"Question":[
      {"identifier":"q1","competencies":[{"code":"no-fractions","level":"proficient"}]}]}}"""
    parseClaims(json)("q1") shouldBe List(CompetencyClaim("no-fractions", "proficient"))
  }

  it should "keep a claim with no level, so a mis-tag is visible rather than silently dropped" in {
    val json = """{"result":{"content":[{"identifier":"c","competencies":[{"code":"x"}]}]}}"""
    parseClaims(json)("c") shouldBe List(CompetencyClaim("x", ""))
  }

  "CompetencyMeta" should "resolve level codes case-insensitively and default unknown ones to 0" in {
    val m = CompetencyMeta("fw", parseLevels(fullFramework), 20, 30, "proficient", Map.empty, Map.empty)
    m.levelIndexOf("PROFICIENT") shouldBe 30
    m.levelIndexOf("nosuchlevel") shouldBe 0
    m.isEmpty shouldBe false
    CompetencyMeta.empty.isEmpty shouldBe true
  }
}
