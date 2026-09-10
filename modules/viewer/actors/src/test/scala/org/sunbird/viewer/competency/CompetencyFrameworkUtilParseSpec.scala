package org.sunbird.viewer.competency

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class CompetencyFrameworkUtilParseSpec extends AnyFlatSpec with Matchers {

  import CompetencyFrameworkUtil._

  // Three tiers, nested by children. Two roles sharing a leaf pool.
  private val healthFramework =
    """{"result":{"framework":{"identifier":"fw_health_competency","type":"Competency",
       "tierLabels":["Competency area","Competency","Skill"],
       "categories":[
         {"code":"competency","terms":[
            {"code":"domain","children":[
               {"code":"medication-administration","children":[
                  {"code":"dosage-calculation"},
                  {"code":"iv-administration"}]},
               {"code":"infection-prevention","children":[
                  {"code":"hand-hygiene"},
                  {"code":"ppe-use"}]}]}]},
         {"code":"role","terms":[
            {"code":"staff-nurse-icu","associations":[
               {"category":"competency","code":"dosage-calculation"},
               {"category":"competency","code":"hand-hygiene"},
               {"category":"competency","code":"ppe-use"}]},
            {"code":"nursing-officer","associations":[
               {"category":"competency","code":"dosage-calculation"},
               {"category":"competency","code":"iv-administration"}]}]}]}}}"""

  // Four tiers, and an uneven branch: measurement is a leaf at tier 2 while fractions reach 4.
  private val schoolFramework =
    """{"result":{"framework":{"identifier":"fw_ncf_outcomes",
       "tierLabels":"Subject area | Competency | Skill | Sub-skill",
       "categories":[
         {"code":"competency","terms":[
            {"code":"numeracy","children":[
               {"code":"number-sense","children":[
                  {"code":"fractions","children":[
                     {"code":"fr-compare"},
                     {"code":"fr-add-unlike"}]}]},
               {"code":"measurement"}]}]},
         {"code":"role","terms":[
            {"code":"grade-6","associations":[
               {"category":"competency","code":"fr-compare"},
               {"category":"competency","code":"measurement"}]}]}]}}}"""

  // A role pointing at a parent term rather than a leaf. The importer should have caught it.
  private val badRoleFramework =
    """{"result":{"framework":{"identifier":"fw_bad",
       "categories":[
         {"code":"competency","terms":[
            {"code":"area","children":[
               {"code":"parent","children":[{"code":"leaf-a"},{"code":"leaf-b"}]}]}]},
         {"code":"role","terms":[
            {"code":"r1","associations":[
               {"category":"competency","code":"parent"},
               {"category":"competency","code":"leaf-a"}]}]}]}}}"""

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

  it should "return nothing for a framework with no competency category" in {
    parseTree("""{"result":{"framework":{"categories":[]}}}""") shouldBe ((Set.empty[String], 0))
  }

  it should "return nothing for an unparseable body" in {
    parseTree("not json") shouldBe ((Set.empty[String], 0))
  }

  "parseTierLabels" should "read a list" in {
    parseTierLabels(healthFramework) shouldBe List("Competency area", "Competency", "Skill")
  }

  it should "read a pipe-separated string, which is what a workbook round-trip produces" in {
    parseTierLabels(schoolFramework) shouldBe
      List("Subject area", "Competency", "Skill", "Sub-skill")
  }

  it should "be empty when the framework does not declare them" in {
    parseTierLabels(flatFramework) shouldBe Nil
  }

  "parseRoles" should "map each role to the leaf skills it requires" in {
    val (leaves, _) = parseTree(healthFramework)
    val roles = parseRoles(healthFramework, leaves)
    roles("staff-nurse-icu") shouldBe Set("dosage-calculation", "hand-hygiene", "ppe-use")
    roles("nursing-officer") shouldBe Set("dosage-calculation", "iv-administration")
  }

  it should "drop an association to a non-leaf term and keep the rest" in {
    val (leaves, _) = parseTree(badRoleFramework)
    parseRoles(badRoleFramework, leaves)("r1") shouldBe Set("leaf-a")
  }

  it should "keep a role that requires nothing, rather than dropping it" in {
    val json = """{"result":{"framework":{"categories":[
      {"code":"competency","terms":[{"code":"a","children":[{"code":"b","children":[{"code":"c"}]}]}]},
      {"code":"role","terms":[{"code":"empty-role"}]}]}}}"""
    val (leaves, _) = parseTree(json)
    parseRoles(json, leaves) shouldBe Map("empty-role" -> Set.empty[String])
  }

  it should "be empty when the framework has no role category" in {
    val (leaves, _) = parseTree(flatFramework)
    parseRoles(flatFramework, leaves) shouldBe Map.empty[String, Set[String]]
  }

  "parseClaims" should "read a flat skills array off a search row" in {
    val json = """{"result":{"content":[
      {"identifier":"crs1","skills":["dosage-calculation","iv-administration"]},
      {"identifier":"crs2","skills":[]}]}}"""
    parseClaims(json) shouldBe Map("crs1" -> List("dosage-calculation", "iv-administration"))
  }

  it should "read a question row the same way" in {
    val json = """{"result":{"questions":[{"identifier":"q1","skills":["fr-compare"]}]}}"""
    parseClaims(json) shouldBe Map("q1" -> List("fr-compare"))
  }

  it should "accept a single string where an array was expected" in {
    val json = """{"result":{"content":[{"identifier":"c","skills":"hand-hygiene"}]}}"""
    parseClaims(json) shouldBe Map("c" -> List("hand-hygiene"))
  }

  it should "de-duplicate a repeated tag" in {
    val json = """{"result":{"content":[{"identifier":"c","skills":["ppe-use","ppe-use"]}]}}"""
    parseClaims(json) shouldBe Map("c" -> List("ppe-use"))
  }

  it should "skip a row with no skills rather than mapping it to an empty list" in {
    val json = """{"result":{"content":[{"identifier":"c"}]}}"""
    parseClaims(json) shouldBe Map.empty[String, List[String]]
  }

  "parseCandidates" should "read the fields the ranking needs" in {
    val json = """{"result":{"content":[
      {"identifier":"do_1","name":"Safe Medication Practice","primaryCategory":"Course",
       "skills":["dosage-calculation","iv-administration"]}]}}"""
    parseCandidates(json) shouldBe List(Candidate("do_1", "Safe Medication Practice", "Course",
      Set("dosage-calculation", "iv-administration")))
  }

  it should "skip a row with no skills, since it can close no gap" in {
    val json = """{"result":{"content":[
      {"identifier":"do_1","name":"Untagged","primaryCategory":"Course"},
      {"identifier":"do_2","name":"Tagged","primaryCategory":"Course","skills":["ppe-use"]}]}}"""
    parseCandidates(json).map(_.id) shouldBe List("do_2")
  }

  it should "fall back to the identifier when a row has no name" in {
    val json = """{"result":{"content":[{"identifier":"do_1","skills":["ppe-use"]}]}}"""
    parseCandidates(json).head.name shouldBe "do_1"
  }

  it should "be empty for an unparseable body" in {
    parseCandidates("not json") shouldBe Nil
  }

  "frameworkField" should "read a scalar off the framework object" in {
    frameworkField(healthFramework, "identifier") shouldBe Some("fw_health_competency")
    frameworkField(healthFramework, "nothing-here") shouldBe None
  }

  "CompetencyMeta" should "be empty when the tree resolved no leaves" in {
    CompetencyMeta.empty.isEmpty shouldBe true
    CompetencyMeta("fw", Nil, Set.empty, 0, Map("r" -> Set("x")), Map.empty).isEmpty shouldBe true
  }

  it should "not be empty merely because it declares no roles" in {
    CompetencyMeta("fw", Nil, Set("leaf"), 3, Map.empty, Map.empty).isEmpty shouldBe false
  }

  it should "answer isLeaf and skillsOf off the resolved tree" in {
    val (leaves, depth) = parseTree(healthFramework)
    val m = CompetencyMeta("fw_health_competency", parseTierLabels(healthFramework), leaves, depth,
      parseRoles(healthFramework, leaves), Map.empty)
    m.isLeaf("ppe-use") shouldBe true
    m.isLeaf("domain") shouldBe false
    m.isLeaf(null) shouldBe false
    m.skillsOf("nursing-officer") shouldBe Set("dosage-calculation", "iv-administration")
    m.skillsOf("no-such-role") shouldBe Set.empty[String]
  }
}
