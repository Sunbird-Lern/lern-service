package org.sunbird.viewer.competency

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class AttainmentRulesSpec extends AnyFlatSpec with Matchers {

  private def ev(skill: String, source: String, occurredOn: Long,
                 revoked: Boolean = false, sourceId: String = "src"): Evidence =
    Evidence(
      userId = "u1", skillId = skill,
      evidenceId = AttainmentRules.evidenceId(occurredOn, source, sourceId, "b1"),
      frameworkId = "fw_health_competency",
      sourceType = source, sourceId = sourceId, batchId = "b1",
      score = None, maxScore = None, issuerId = None, note = None,
      occurredOn = occurredOn, revoked = revoked)

  "fullMarks" should "hold when every question is at its maximum" in {
    AttainmentRules.fullMarks(List((1d, 1d), (2d, 2d), (1d, 1d))) shouldBe true
  }

  it should "not hold when one question of several is short" in {
    AttainmentRules.fullMarks(List((1d, 1d), (1d, 2d), (1d, 1d))) shouldBe false
  }

  it should "hold on a single question at full marks" in {
    AttainmentRules.fullMarks(List((5d, 5d))) shouldBe true
  }

  it should "not hold when nothing was asked" in {
    AttainmentRules.fullMarks(Nil) shouldBe false
  }

  it should "not hold when a question carries no marks, rather than passing vacuously" in {
    AttainmentRules.fullMarks(List((0d, 0d))) shouldBe false
    AttainmentRules.fullMarks(List((1d, 1d), (0d, 0d))) shouldBe false
  }

  it should "hold when a score exceeds the maximum, which bonus marking can produce" in {
    AttainmentRules.fullMarks(List((3d, 2d))) shouldBe true
  }

  "earnedIn" should "return only the skills whose every question is at full marks" in {
    AttainmentRules.earnedIn(Map(
      "hand-hygiene" -> List((1d, 1d), (1d, 1d)),
      "ppe-use" -> List((1d, 1d)),
      "sterile-field" -> List((1d, 1d), (0d, 1d)),
      "dosage-calculation" -> List((2d, 5d)))) shouldBe Set("hand-hygiene", "ppe-use")
  }

  it should "return nothing when the attempt earned nothing" in {
    AttainmentRules.earnedIn(Map("a" -> List((0d, 1d)))) shouldBe Set.empty[String]
    AttainmentRules.earnedIn(Map.empty) shouldBe Set.empty[String]
  }

  "earnedAcross" should "union attempts rather than take a best one" in {
    // attempt 1 scored less overall but got hand-hygiene right; attempt 2 got ppe-use right.
    // Taking a "best" attempt by total score would lose one of them.
    val attempt1 = Map("hand-hygiene" -> List((1d, 1d)), "ppe-use" -> List((0d, 1d)))
    val attempt2 = Map("hand-hygiene" -> List((0d, 1d)), "ppe-use" -> List((1d, 1d)))
    AttainmentRules.earnedAcross(List(attempt1, attempt2)) shouldBe Set("hand-hygiene", "ppe-use")
  }

  it should "not lose a skill earned only in the earliest attempt" in {
    val early = Map("dosage-calculation" -> List((5d, 5d)))
    val later = Map("dosage-calculation" -> List((3d, 5d)))
    AttainmentRules.earnedAcross(List(early, later)) shouldBe Set("dosage-calculation")
  }

  it should "return nothing when there are no attempts" in {
    AttainmentRules.earnedAcross(Nil) shouldBe Set.empty[String]
  }

  "project" should "report a skill held on one live row" in {
    val entry = AttainmentRules.project(List(ev("hand-hygiene", SourceType.COURSE, 1000L)))
    entry.map(_.skillId) shouldBe Some("hand-hygiene")
    entry.map(_.sourceType) shouldBe Some(SourceType.COURSE)
  }

  it should "report nothing when there is no evidence at all" in {
    AttainmentRules.project(Nil) shouldBe None
  }

  it should "report nothing when every row is revoked" in {
    AttainmentRules.project(List(
      ev("ppe-use", SourceType.COURSE, 1000L, revoked = true),
      ev("ppe-use", SourceType.ASSESSMENT, 2000L, revoked = true))) shouldBe None
  }

  it should "ignore revoked rows but keep the skill when a live one remains" in {
    val entry = AttainmentRules.project(List(
      ev("ppe-use", SourceType.COURSE, 1000L, revoked = true),
      ev("ppe-use", SourceType.ASSESSMENT, 2000L)))
    entry.map(_.sourceType) shouldBe Some(SourceType.ASSESSMENT)
  }

  it should "date attainment to the earliest live row, so it does not move as evidence accrues" in {
    val rows = List(
      ev("dosage-calculation", SourceType.ASSESSMENT, 5000L),
      ev("dosage-calculation", SourceType.EXTERNAL, 1000L),
      ev("dosage-calculation", SourceType.COURSE, 3000L))
    val entry = AttainmentRules.project(rows)
    entry.map(_.attainedOn) shouldBe Some(1000L)
    entry.map(_.sourceType) shouldBe Some(SourceType.EXTERNAL)
  }

  it should "date attainment to the earliest live row even when an earlier one is revoked" in {
    val rows = List(
      ev("dosage-calculation", SourceType.EXTERNAL, 1000L, revoked = true),
      ev("dosage-calculation", SourceType.COURSE, 3000L))
    AttainmentRules.project(rows).map(_.attainedOn) shouldBe Some(3000L)
  }

  it should "break a same-millisecond tie deterministically" in {
    val a = ev("s", SourceType.COURSE, 1000L, sourceId = "aaa")
    val b = ev("s", SourceType.COURSE, 1000L, sourceId = "bbb")
    AttainmentRules.project(List(a, b)) shouldBe AttainmentRules.project(List(b, a))
  }

  "isHeld" should "agree with project" in {
    AttainmentRules.isHeld(List(ev("s", SourceType.COURSE, 1L))) shouldBe true
    AttainmentRules.isHeld(List(ev("s", SourceType.COURSE, 1L, revoked = true))) shouldBe false
    AttainmentRules.isHeld(Nil) shouldBe false
  }

  "transitions" should "name what was gained and what was lost" in {
    AttainmentRules.transitions(
      before = Set("hand-hygiene", "ppe-use"),
      after = Set("ppe-use", "dosage-calculation")) shouldBe
      ((Set("dosage-calculation"), Set("hand-hygiene")))
  }

  it should "report nothing when the profile is unchanged, which is what makes badges idempotent" in {
    val held = Set("hand-hygiene", "ppe-use")
    AttainmentRules.transitions(held, held) shouldBe ((Set.empty[String], Set.empty[String]))
  }

  it should "report a first attainment" in {
    AttainmentRules.transitions(Set.empty, Set("ppe-use")) shouldBe
      ((Set("ppe-use"), Set.empty[String]))
  }

  it should "report a full revocation" in {
    AttainmentRules.transitions(Set("ppe-use"), Set.empty) shouldBe
      ((Set.empty[String], Set("ppe-use")))
  }

  "evidenceId" should "be stable for the same source, so a replay rewrites one row" in {
    AttainmentRules.evidenceId(1700000000000L, SourceType.COURSE, "do_course", "b1") shouldBe
      AttainmentRules.evidenceId(1700000000000L, SourceType.COURSE, "do_course", "b1")
  }

  it should "differ when the source differs" in {
    AttainmentRules.evidenceId(1L, SourceType.COURSE, "do_a", "b1") should not be
      AttainmentRules.evidenceId(1L, SourceType.COURSE, "do_b", "b1")
  }

  it should "sort chronologically as a string, which is how the ledger clusters" in {
    val early = AttainmentRules.evidenceId(999L, SourceType.COURSE, "x", "b")
    val late = AttainmentRules.evidenceId(1000L, SourceType.COURSE, "x", "b")
    early < late shouldBe true
  }
}
