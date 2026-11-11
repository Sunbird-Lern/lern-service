package org.sunbird.viewer.competency

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class CompetencyFrameworkUtilParseSpec extends AnyFlatSpec with Matchers {

  import CompetencyFrameworkUtil._

  // Three tiers, nested by children. Two roles sharing a leaf pool.
  private val healthFramework =
    """{"result":{"framework":{"identifier":"fw_health_competency","objectType":"CompetencyFramework",
       "categories":[
         {"code":"competency","terms":[
            {"code":"domain","children":[
               {"code":"medication-administration","children":[
                  {"code":"dosage-calculation"},
                  {"code":"iv-administration"}]},
               {"code":"infection-prevention","children":[
                  {"code":"hand-hygiene"},
                  {"code":"ppe-use"}]}]}]}]}}}"""

  // Four tiers, and an uneven branch: measurement is a leaf at tier 2 while fractions reach 4.
  private val schoolFramework =
    """{"result":{"framework":{"identifier":"fw_ncf_outcomes",
       "categories":[
         {"code":"competency","terms":[
            {"code":"numeracy","children":[
               {"code":"number-sense","children":[
                  {"code":"fractions","children":[
                     {"code":"fr-compare"},
                     {"code":"fr-add-unlike"}]}]},
               {"code":"measurement"}]}]}]}}}"""

  // Flat term list carrying parentCode instead of nested children.
  private val flatFramework =
    """{"result":{"framework":{"identifier":"fw_flat",
       "categories":[
         {"code":"competency","terms":[
            {"code":"area"},
            {"code":"mid","parentCode":"area"},
            {"code":"leaf-1","parentCode":"mid"},
            {"code":"leaf-2","parentCode":"mid"}]}]}}}"""

  "parseTree" should "find the leaves of a three-tier nested tree" in {
    val (leaves, depth) = parseTree(healthFramework)
    leaves shouldBe Set("dosage-calculation", "iv-administration", "hand-hygiene", "ppe-use")
    depth shouldBe 3
  }

  it should "not treat a term with children as a leaf" in {
    val (leaves, _) = parseTree(healthFramework)
    leaves should not contain "domain"
    leaves should not contain "medication-administration"
  }

  it should "handle a four-tier tree and an unevenly deep branch" in {
    val (leaves, depth) = parseTree(schoolFramework)
    leaves shouldBe Set("fr-compare", "fr-add-unlike", "measurement")
    depth shouldBe 4
  }

  it should "derive leaves from parentCode when the terms are flat" in {
    val (leaves, depth) = parseTree(flatFramework)
    leaves shouldBe Set("leaf-1", "leaf-2")
    depth shouldBe 3
  }

  it should "return nothing for a framework with no categories" in {
    parseTree("""{"result":{"framework":{"categories":[]}}}""") shouldBe ((Set.empty[String], 0))
  }

  it should "return nothing for an unparseable body" in {
    parseTree("not json") shouldBe ((Set.empty[String], 0))
  }

  // The role -> skill map is authored in `role_skill` and read through the DAO, NOT parsed out of
  // the framework. `leafOnly` is all that stands between an authored row and readiness, because
  // nothing validates it at write time against a tree the writer cannot see.
  // The tiers live in SEPARATE categories, joined by associations. The last category by index
  // holds the skills, whatever the adopter names it.
  private val assocFramework =
    """{"result":{"framework":{"categories":[
      {"code":"competencyarea","index":1,"terms":[
        {"identifier":"fw_competencyarea_domain","code":"domain","category":"competencyarea",
         "associations":[{"identifier":"fw_competency_med-admin","code":"med-admin","category":"competency"}]}]},
      {"code":"competency","index":2,"terms":[
        {"identifier":"fw_competency_med-admin","code":"med-admin","category":"competency",
         "associations":[
           {"identifier":"fw_skill_dosage","code":"dosage","category":"skill"},
           {"identifier":"fw_skill_iv","code":"iv","category":"skill"}]},
        {"identifier":"fw_competency_comms","code":"comms","category":"competency",
         "associations":[{"identifier":"fw_skill_counselling","code":"counselling","category":"skill"}]}]},
      {"code":"skill","index":3,"terms":[
        {"identifier":"fw_skill_dosage","code":"dosage","category":"skill"},
        {"identifier":"fw_skill_iv","code":"iv","category":"skill"},
        {"identifier":"fw_skill_counselling","code":"counselling","category":"skill"}]}]}}}"""

  "parseTree" should "take the skills from the last category by index" in {
    val (leaves, depth) = parseTree(assocFramework)
    leaves shouldBe Set("dosage", "iv", "counselling")
    depth shouldBe 3
  }

  it should "not take a term from a category above the skills" in {
    val (leaves, _) = parseTree(assocFramework)
    leaves should not contain "med-admin"
    leaves should not contain "comms"
  }

  it should "ignore associations between terms of the skill category" in {
    val sideways =
      """{"result":{"framework":{"categories":[
        {"code":"competency","terms":[
          {"identifier":"fw_competency_a","code":"a","category":"competency",
           "associations":[{"identifier":"fw_competency_b","code":"b","category":"competency"}]},
          {"identifier":"fw_competency_b","code":"b","category":"competency"}]}]}}}"""
    val (leaves, depth) = parseTree(sideways)
    leaves shouldBe Set("a", "b")
    depth shouldBe 1
  }

  it should "terminate on an association cycle in authored data" in {
    val cyclic =
      """{"result":{"framework":{"categories":[
        {"code":"competency","terms":[
          {"identifier":"fw_competency_a","code":"a","category":"competency",
           "associations":[{"identifier":"fw_skill_x","code":"x","category":"skill"}]}]},
        {"code":"skill","terms":[
          {"identifier":"fw_skill_x","code":"x","category":"skill",
           "associations":[{"identifier":"fw_competency_a","code":"a","category":"competency"}]}]}]}}}"""
    val (leaves, _) = parseTree(cyclic)
    leaves should not be empty
  }

  it should "ignore children nested in a category above the skills" in {
    val both =
      """{"result":{"framework":{"categories":[
        {"code":"competency","terms":[
          {"identifier":"fw_competency_a","code":"a","category":"competency",
           "children":[{"identifier":"fw_competency_kid","code":"kid","category":"competency"}],
           "associations":[{"identifier":"fw_skill_other","code":"other","category":"skill"}]}]},
        {"code":"skill","terms":[{"identifier":"fw_skill_other","code":"other","category":"skill"}]}]}}}"""
    val (leaves, _) = parseTree(both)
    leaves shouldBe Set("other")
  }

  it should "order categories by index, not by their position in the response" in {
    val shuffled =
      """{"result":{"framework":{"categories":[
        {"code":"capability","index":3,"terms":[{"code":"c1"},{"code":"c2"}]},
        {"code":"area","index":1,"terms":[{"code":"a1"}]},
        {"code":"cluster","index":2,"terms":[{"code":"k1"}]}]}}}"""
    parseTree(shuffled) shouldBe ((Set("c1", "c2"), 3))
    parseSkillField(shuffled) shouldBe Some("capability")
  }

  it should "fall back to position when categories carry no index" in {
    parseSkillField(assocFramework.replaceAll(",\"index\":\\d", "")) shouldBe Some("skill")
    parseSkillField(healthFramework) shouldBe Some("competency")
  }

  "leafOnly" should "keep the requirements that name a real leaf" in {
    val (leaves, _) = parseTree(healthFramework)
    val authored = Map(
      "staff-nurse-icu" -> Set("dosage-calculation", "hand-hygiene", "ppe-use"),
      "nursing-officer" -> Set("dosage-calculation", "iv-administration"))
    val roles = leafOnly("fw", authored, leaves)
    roles("staff-nurse-icu") shouldBe Set("dosage-calculation", "hand-hygiene", "ppe-use")
    roles("nursing-officer") shouldBe Set("dosage-calculation", "iv-administration")
  }

  // An interior term is never tagged, so no evidence path exists and the skill can never be held.
  // Crediting it would report a learner ready for a role they cannot complete.
  it should "drop a requirement naming an interior term and keep the rest" in {
    val (leaves, _) = parseTree(healthFramework)
    leafOnly("fw", Map("r1" -> Set("dosage-calculation", "domain")), leaves) shouldBe
      Map("r1" -> Set("dosage-calculation"))
  }

  it should "drop a requirement naming a code absent from the tree" in {
    val (leaves, _) = parseTree(healthFramework)
    leafOnly("fw", Map("r1" -> Set("dosage-calculation", "retired-code")), leaves) shouldBe
      Map("r1" -> Set("dosage-calculation"))
  }

  it should "keep a role that requires nothing, rather than dropping it" in {
    val (leaves, _) = parseTree(healthFramework)
    leafOnly("fw", Map("empty-role" -> Set.empty[String]), leaves) shouldBe
      Map("empty-role" -> Set.empty[String])
  }

  it should "be empty when no roles are authored" in {
    val (leaves, _) = parseTree(healthFramework)
    leafOnly("fw", Map.empty, leaves) shouldBe Map.empty[String, Set[String]]
  }

  "parseClaims" should "read a flat skill array off a search row" in {
    val json = """{"result":{"content":[
      {"identifier":"crs1","skill":["dosage-calculation","iv-administration"]},
      {"identifier":"crs2","skill":[]}]}}"""
    parseClaims(json, "skill") shouldBe Map("crs1" -> List("dosage-calculation", "iv-administration"))
  }

  it should "read a question row the same way" in {
    val json = """{"result":{"questions":[{"identifier":"q1","skill":["fr-compare"]}]}}"""
    parseClaims(json, "skill") shouldBe Map("q1" -> List("fr-compare"))
  }

  it should "accept a single string where an array was expected" in {
    val json = """{"result":{"content":[{"identifier":"c","skill":"hand-hygiene"}]}}"""
    parseClaims(json, "skill") shouldBe Map("c" -> List("hand-hygiene"))
  }

  it should "de-duplicate a repeated tag" in {
    val json = """{"result":{"content":[{"identifier":"c","skill":["ppe-use","ppe-use"]}]}}"""
    parseClaims(json, "skill") shouldBe Map("c" -> List("ppe-use"))
  }

  it should "skip a row with no skills rather than mapping it to an empty list" in {
    val json = """{"result":{"content":[{"identifier":"c"}]}}"""
    parseClaims(json, "skill") shouldBe Map.empty[String, List[String]]
  }

  it should "read whichever field the framework names" in {
    val json = """{"result":{"content":[{"identifier":"c","capability":["c1"],"skill":["x"]}]}}"""
    parseClaims(json, "capability") shouldBe Map("c" -> List("c1"))
  }

  "parseCandidates" should "read the fields the ranking needs" in {
    val json = """{"result":{"content":[
      {"identifier":"do_1","name":"Safe Medication Practice","primaryCategory":"Course",
       "skill":["dosage-calculation","iv-administration"]}]}}"""
    parseCandidates(json, "skill") shouldBe List(Candidate("do_1", "Safe Medication Practice", "Course",
      Set("dosage-calculation", "iv-administration")))
  }

  it should "skip a row with no skills, since it can close no gap" in {
    val json = """{"result":{"content":[
      {"identifier":"do_1","name":"Untagged","primaryCategory":"Course"},
      {"identifier":"do_2","name":"Tagged","primaryCategory":"Course","skill":["ppe-use"]}]}}"""
    parseCandidates(json, "skill").map(_.id) shouldBe List("do_2")
  }

  it should "fall back to the identifier when a row has no name" in {
    val json = """{"result":{"content":[{"identifier":"do_1","skill":["ppe-use"]}]}}"""
    parseCandidates(json, "skill").head.name shouldBe "do_1"
  }

  it should "be empty for an unparseable body" in {
    parseCandidates("not json", "skill") shouldBe Nil
  }

  "frameworkField" should "read a scalar off the framework object" in {
    frameworkField(healthFramework, "identifier") shouldBe Some("fw_health_competency")
    frameworkField(healthFramework, "nothing-here") shouldBe None
  }

  "CompetencyMeta" should "be empty when the tree resolved no leaves" in {
    CompetencyMeta.empty.isEmpty shouldBe true
    CompetencyMeta("fw", "skill", Set.empty, 0, Map("r" -> Set("x")), Map.empty).isEmpty shouldBe true
  }

  it should "not be empty merely because it declares no roles" in {
    CompetencyMeta("fw", "skill", Set("leaf"), 3, Map.empty, Map.empty).isEmpty shouldBe false
  }

  it should "answer isLeaf and skillsOf off the resolved tree" in {
    val (leaves, depth) = parseTree(healthFramework)
    val m = CompetencyMeta("fw_health_competency", "competency", leaves, depth,
      Map("nursing-officer" -> Set("dosage-calculation", "iv-administration")), Map.empty)
    m.isLeaf("ppe-use") shouldBe true
    m.isLeaf("domain") shouldBe false
    m.isLeaf(null) shouldBe false
    m.skillsOf("nursing-officer") shouldBe Set("dosage-calculation", "iv-administration")
    m.skillsOf("no-such-role") shouldBe Set.empty[String]
  }
}
