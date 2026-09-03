package org.sunbird.viewer.competency

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class AttainmentRulesSpec extends AnyFlatSpec with Matchers {

  // sparse indices, so a level can be inserted later without renumbering stored level_index
  private val scale = List(
    LevelDef("beginning", 10, 40, 4, None),
    LevelDef("progressing", 20, 55, 5, None),
    LevelDef("proficient", 30, 70, 6, None),
    LevelDef("advanced", 40, 85, 8, None))

  private def ev(level: String, index: Int, occurredOn: Long = 1000L,
                 expiresOn: Option[Long] = None, revoked: Boolean = false,
                 sourceType: String = SourceType.ASSESSMENT): Evidence =
    Evidence("u1", "c1", s"$occurredOn:$level", "fw", level, index, sourceType, "src", "b1",
      None, None, 0, None, None, occurredOn, expiresOn, revoked, None)

  "band" should "pick the highest level whose cut-score and evidence bar are both met" in {
    AttainmentRules.band(76, 8, scale).map(_.code) shouldBe Some("proficient")
    AttainmentRules.band(90, 9, scale).map(_.code) shouldBe Some("advanced")
  }

  it should "not award a level when too few questions were attempted" in {
    // 88% clears advanced's 85 cut-score, but advanced needs 8 questions and only 6 were asked
    AttainmentRules.band(88, 6, scale).map(_.code) shouldBe Some("proficient")
  }

  it should "return None below the lowest band" in {
    AttainmentRules.band(35, 10, scale) shouldBe None
    AttainmentRules.band(100, 1, scale) shouldBe None // fails every minEvidenceCount
  }

  it should "treat the cut-score as inclusive" in {
    AttainmentRules.band(70, 6, scale).map(_.code) shouldBe Some("proficient")
  }

  "pct" should "be zero when nothing was attemptable" in {
    AttainmentRules.pct(0, 0) shouldBe 0d
    AttainmentRules.pct(5, 10) shouldBe 50d
  }

  "capCompletion" should "hold a completion-derived claim to the framework cap" in {
    AttainmentRules.capCompletion(40, 20) shouldBe 20
    AttainmentRules.capCompletion(10, 20) shouldBe 10 // already under the cap
    AttainmentRules.capCompletion(40, 0) shouldBe 40  // uncapped
  }

  "project" should "hold the maximum level over live evidence" in {
    val entry = AttainmentRules.project(List(ev("progressing", 20), ev("proficient", 30)), 5000L, 0L)
    entry.map(_.level) shouldBe Some("proficient")
    entry.map(_.status) shouldBe Some(AttainmentRules.ATTAINED)
  }

  it should "not let a later weaker attempt demote the learner" in {
    val strong = ev("advanced", 40, occurredOn = 1000L)
    val weak = ev("progressing", 20, occurredOn = 9000L)
    AttainmentRules.project(List(strong, weak), 10000L, 0L).map(_.levelIndex) shouldBe Some(40)
  }

  it should "ignore revoked evidence" in {
    val entry = AttainmentRules.project(
      List(ev("advanced", 40, revoked = true), ev("progressing", 20)), 5000L, 0L)
    entry.map(_.level) shouldBe Some("progressing")
  }

  it should "return None when every row is revoked" in {
    AttainmentRules.project(List(ev("advanced", 40, revoked = true)), 5000L, 0L) shouldBe None
  }

  it should "fall back to the highest level still supported when the top one lapses" in {
    val lapsed = ev("advanced", 40, occurredOn = 1000L, expiresOn = Some(2000L))
    val current = ev("proficient", 30, occurredOn = 1500L)
    val entry = AttainmentRules.project(List(lapsed, current), 5000L, 0L)
    entry.map(_.level) shouldBe Some("proficient")
    entry.map(_.status) shouldBe Some(AttainmentRules.ATTAINED)
  }

  it should "mark EXPIRED and keep the lapsed claim when nothing is current" in {
    val entry = AttainmentRules.project(
      List(ev("advanced", 40, expiresOn = Some(2000L))), 5000L, 0L)
    entry.map(_.status) shouldBe Some(AttainmentRules.EXPIRED)
    entry.map(_.level) shouldBe Some("advanced") // retained in history, not deleted
  }

  it should "mark EXPIRING inside the window" in {
    val entry = AttainmentRules.project(
      List(ev("proficient", 30, expiresOn = Some(6000L))), 5000L, 2000L)
    entry.map(_.status) shouldBe Some(AttainmentRules.EXPIRING)
  }

  it should "report IN_PROGRESS for sub-threshold evidence" in {
    AttainmentRules.project(List(ev("", 0)), 5000L, 0L).map(_.status) shouldBe
      Some(AttainmentRules.IN_PROGRESS)
  }

  it should "return None for no evidence at all" in {
    AttainmentRules.project(Nil, 5000L, 0L) shouldBe None
  }

  "expiryOf" should "add the validity window, and be absent when the framework sets none" in {
    AttainmentRules.expiryOf(0L, None) shouldBe None
    AttainmentRules.expiryOf(0L, Some(0)) shouldBe None
    AttainmentRules.expiryOf(0L, Some(1)).get should be > 0L
  }

  "expiryBucket" should "bucket by UTC month" in {
    AttainmentRules.expiryBucket(0L) shouldBe "1970-01"
  }

  "evidenceId" should "be stable for the same source, so replays overwrite rather than duplicate" in {
    val a = AttainmentRules.evidenceId(1234L, SourceType.COURSE, "crs1", "b1")
    val b = AttainmentRules.evidenceId(1234L, SourceType.COURSE, "crs1", "b1")
    a shouldBe b
    a should not be AttainmentRules.evidenceId(1234L, SourceType.COURSE, "crs2", "b1")
  }

  it should "sort chronologically as text" in {
    val early = AttainmentRules.evidenceId(1000L, SourceType.COURSE, "c", "b")
    val late = AttainmentRules.evidenceId(2000L, SourceType.COURSE, "c", "b")
    early < late shouldBe true
  }
}
