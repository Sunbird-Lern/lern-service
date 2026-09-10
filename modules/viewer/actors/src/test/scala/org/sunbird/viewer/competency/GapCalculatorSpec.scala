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
}
