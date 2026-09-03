package org.sunbird.viewer.competency

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class GapCalculatorSpec extends AnyFlatSpec with Matchers {

  private val reqs = List(
    RequirementDef("medication", "l3", 30, Criticality.MANDATORY),
    RequirementDef("infection", "l3", 30, Criticality.MANDATORY),
    RequirementDef("comms", "l2", 20, Criticality.MANDATORY),
    RequirementDef("healthdata", "l1", 10, Criticality.DESIRABLE))

  "rows" should "classify each requirement as MET, BELOW or MISSING" in {
    val held = Map("medication" -> ("l4", 40), "infection" -> ("l2", 20))
    val byId = GapCalculator.rows(reqs, held).map(r => r.competencyId -> r.status).toMap
    byId("medication") shouldBe GapCalculator.MET     // held above required
    byId("infection") shouldBe GapCalculator.BELOW    // held, but short
    byId("comms") shouldBe GapCalculator.MISSING
    byId("healthdata") shouldBe GapCalculator.MISSING
  }

  it should "treat an exactly-equal level as MET" in {
    GapCalculator.rows(List(reqs.head), Map("medication" -> ("l3", 30))).head.status shouldBe
      GapCalculator.MET
  }

  "readiness" should "count mandatory requirements only" in {
    val held = Map("medication" -> ("l4", 40), "infection" -> ("l3", 30), "comms" -> ("l2", 20))
    // all three mandatory met; the desirable one is still missing
    GapCalculator.readiness(GapCalculator.rows(reqs, held)) shouldBe 100
  }

  it should "not be inflated by desirable rows" in {
    val held = Map("healthdata" -> ("l1", 10))
    GapCalculator.readiness(GapCalculator.rows(reqs, held)) shouldBe 0
  }

  it should "be 100 when a position declares no mandatory requirements" in {
    GapCalculator.readiness(Nil) shouldBe 100
    GapCalculator.readiness(GapCalculator.rows(
      List(RequirementDef("x", "l1", 10, Criticality.DESIRABLE)), Map.empty)) shouldBe 100
  }

  it should "round down rather than up" in {
    val held = Map("medication" -> ("l3", 30))
    GapCalculator.readiness(GapCalculator.rows(reqs, held)) shouldBe 33 // 1 of 3 mandatory
  }

  "isMandatory" should "default an unset criticality to mandatory" in {
    GapCalculator.isMandatory(GapRow("c", "l1", 10, "", 0, null, GapCalculator.MISSING)) shouldBe true
    GapCalculator.isMandatory(GapRow("c", "l1", 10, "", 0, "", GapCalculator.MISSING)) shouldBe true
  }

  "outstanding" should "drop met rows and put mandatory ones first" in {
    val held = Map("medication" -> ("l4", 40))
    val out = GapCalculator.outstanding(GapCalculator.rows(reqs, held))
    out.map(_.competencyId) should not contain "medication"
    out.head.criticality shouldBe Criticality.MANDATORY
    out.last.competencyId shouldBe "healthdata" // desirable sinks to the bottom
  }
}
