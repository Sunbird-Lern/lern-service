package org.sunbird.viewer.competency

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class GapCalculatorSpec extends AnyFlatSpec with Matchers {

  // staff-nurse-icu, trimmed to four skills
  private val required = Set("dosage-calculation", "hand-hygiene", "ppe-use", "hmis-reporting")

  "rows" should "classify each required skill as MET or MISSING" in {
    val held = Set("dosage-calculation", "hand-hygiene", "unrelated-skill")
    val byId = GapCalculator.rows(required, held).map(r => r.skillId -> r.status).toMap
    byId("dosage-calculation") shouldBe GapCalculator.MET
    byId("hand-hygiene") shouldBe GapCalculator.MET
    byId("ppe-use") shouldBe GapCalculator.MISSING
    byId("hmis-reporting") shouldBe GapCalculator.MISSING
  }

  it should "report only the required skills, ignoring extras the learner holds" in {
    val rows = GapCalculator.rows(required, Set("something-else", "and-another"))
    rows.map(_.skillId) should contain theSameElementsAs required.toList
    rows.map(_.status).distinct shouldBe List(GapCalculator.MISSING)
  }

  it should "order rows by skill code so the output is stable" in {
    GapCalculator.rows(required, Set.empty).map(_.skillId) shouldBe required.toList.sorted
  }

  it should "return nothing when the role requires nothing" in {
    GapCalculator.rows(Set.empty, Set("dosage-calculation")) shouldBe Nil
  }

  "readiness" should "be 0 when nothing is held" in {
    GapCalculator.readiness(GapCalculator.rows(required, Set.empty)) shouldBe 0
  }

  it should "be 100 when every required skill is held" in {
    GapCalculator.readiness(GapCalculator.rows(required, required)) shouldBe 100
  }

  it should "be the whole-number percentage of met over required" in {
    // one of four met
    GapCalculator.readiness(GapCalculator.rows(required, Set("ppe-use"))) shouldBe 25
    // two of four
    GapCalculator.readiness(GapCalculator.rows(required, Set("ppe-use", "hand-hygiene"))) shouldBe 50
  }

  it should "truncate rather than round, so 100 means everything" in {
    // two of three is 66.67; it must not read as 67 and must never read as 100
    val three = Set("a", "b", "c")
    GapCalculator.readiness(GapCalculator.rows(three, Set("a", "b"))) shouldBe 66
  }

  it should "be 100 for a role that declares no skills" in {
    GapCalculator.readiness(Nil) shouldBe 100
  }

  "outstanding" should "list only the missing skills" in {
    val rows = GapCalculator.rows(required, Set("dosage-calculation", "hand-hygiene"))
    GapCalculator.outstanding(rows) shouldBe List("hmis-reporting", "ppe-use")
  }

  "met" should "list only the held skills" in {
    val rows = GapCalculator.rows(required, Set("dosage-calculation", "hand-hygiene"))
    GapCalculator.met(rows) shouldBe List("dosage-calculation", "hand-hygiene")
  }

  // ---- recommendation -------------------------------------------------------------------------

  private def cand(id: String, skills: String*): Candidate =
    Candidate(id, id + " name", "Course", skills.toSet)

  "rankCandidates" should "put the candidate covering most of the gap first" in {
    val ranked = GapCalculator.rankCandidates(
      candidates = List(
        cand("one-skill", "ppe-use"),
        cand("two-skills", "ppe-use", "hmis-reporting")),
      outstanding = Set("ppe-use", "hmis-reporting"),
      held = Set.empty)
    ranked.map(_.candidate.id) shouldBe List("two-skills", "one-skill")
    ranked.head.gapCovered shouldBe 2
  }

  it should "drop a candidate that covers none of the gap" in {
    GapCalculator.rankCandidates(
      List(cand("unrelated", "budget-preparation")),
      outstanding = Set("ppe-use"),
      held = Set.empty) shouldBe Nil
  }

  it should "prefer the candidate with less the learner already holds, as an effort proxy" in {
    // both close the same one gap skill; "lean" wastes nothing, "padded" re-covers two held skills
    val ranked = GapCalculator.rankCandidates(
      candidates = List(
        cand("padded", "ppe-use", "hand-hygiene", "dosage-calculation"),
        cand("lean", "ppe-use")),
      outstanding = Set("ppe-use"),
      held = Set("hand-hygiene", "dosage-calculation"))
    ranked.map(_.candidate.id) shouldBe List("lean", "padded")
    ranked.head.alreadyHeld shouldBe 0
    ranked.last.alreadyHeld shouldBe 2
  }

  it should "prefer the tighter fit when gap covered and held are equal" in {
    val ranked = GapCalculator.rankCandidates(
      candidates = List(
        cand("sprawling", "ppe-use", "x", "y", "z"),
        cand("tight", "ppe-use")),
      outstanding = Set("ppe-use"),
      held = Set.empty)
    ranked.map(_.candidate.id) shouldBe List("tight", "sprawling")
  }

  it should "order deterministically when everything else ties" in {
    val a = cand("aaa", "ppe-use")
    val b = cand("bbb", "ppe-use")
    val one = GapCalculator.rankCandidates(List(a, b), Set("ppe-use"), Set.empty)
    val two = GapCalculator.rankCandidates(List(b, a), Set("ppe-use"), Set.empty)
    one.map(_.candidate.id) shouldBe two.map(_.candidate.id)
    one.map(_.candidate.id) shouldBe List("aaa", "bbb")
  }

  it should "return nothing when there are no candidates" in {
    GapCalculator.rankCandidates(Nil, Set("ppe-use"), Set.empty) shouldBe Nil
  }

  // ---- coverage -------------------------------------------------------------------------------

  "coverage" should "name every course that teaches a required skill" in {
    val byCourse = Map(
      "safe-medication" -> List("dosage-calculation"),
      "infection-control" -> List("hand-hygiene", "ppe-use"),
      "refresher" -> List("hand-hygiene"))
    val rows = GapCalculator.coverage(required, byCourse)
    val byId = rows.map(r => r.skillId -> r).toMap
    byId("hand-hygiene").taughtBy shouldBe List("infection-control", "refresher")
    byId("hand-hygiene").status shouldBe GapCalculator.COVERED
    byId("dosage-calculation").taughtBy shouldBe List("safe-medication")
  }

  it should "report a required skill nothing teaches, rather than omitting it" in {
    val rows = GapCalculator.coverage(required, Map("c1" -> List("dosage-calculation")))
    val notCovered = rows.filter(_.status == GapCalculator.NOT_COVERED).map(_.skillId)
    notCovered shouldBe List("hand-hygiene", "hmis-reporting", "ppe-use")
    rows.filter(_.status == GapCalculator.NOT_COVERED).flatMap(_.taughtBy) shouldBe Nil
  }

  it should "ignore course skills the role does not require" in {
    val rows = GapCalculator.coverage(Set("ppe-use"), Map("c1" -> List("ppe-use", "team-briefing")))
    rows.map(_.skillId) shouldBe List("ppe-use")
  }

  it should "return nothing when the role requires nothing" in {
    GapCalculator.coverage(Set.empty, Map("c1" -> List("ppe-use"))) shouldBe Nil
  }

  "unassessed" should "name skills taught but never measured" in {
    GapCalculator.unassessed(
      taught = Set("ppe-use", "hand-hygiene", "dosage-calculation"),
      assessed = Set("hand-hygiene")) shouldBe List("dosage-calculation", "ppe-use")
  }

  it should "be empty when every taught skill is measured somewhere" in {
    GapCalculator.unassessed(Set("ppe-use"), Set("ppe-use", "extra")) shouldBe Nil
  }
}
