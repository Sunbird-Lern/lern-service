package org.sunbird.viewer.competency

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class LedgerSnapshotSpec extends AnyFlatSpec with Matchers {

  private def ev(skill: String, source: String, sourceId: String, occurredOn: Long,
                 revoked: Boolean = false): Evidence =
    Evidence(
      userId = "u1", skillId = skill,
      evidenceId = AttainmentRules.evidenceId(occurredOn, source, sourceId, "b1"),
      frameworkId = "fw", sourceType = source, sourceId = sourceId, batchId = "b1",
      score = None, maxScore = None, issuerId = None, note = None,
      occurredOn = occurredOn, revoked = revoked)

  /** Stands in for the partition read: counts reads and returns whatever the "table" holds now. */
  private class Table(var rows: List[Evidence]) {
    var reads = 0
    def read(): List[Evidence] = { reads += 1; rows }
  }

  // Every aggregation re-credits every completed course; one that teaches no skill must cost nothing.
  "LedgerSnapshot" should "not read anything until it is first used" in {
    val t = new Table(Nil)
    val snap = new LedgerSnapshot("u1", t.read())
    t.reads shouldBe 0
    snap.isLoaded shouldBe false
  }

  it should "read at most once, however many times it is used" in {
    val t = new Table(List(ev("a", SourceType.COURSE, "c1", 1000L)))
    val snap = new LedgerSnapshot("u1", t.read())
    snap.ofSkill("a"); snap.current; snap.before
    snap.added(ev("b", SourceType.COURSE, "c2", 2000L))
    t.reads shouldBe 1
    snap.isLoaded shouldBe true
  }

  // The ledger touches the snapshot before its own Cassandra write; `before` must be that state,
  // not what the table holds once the write has landed.
  it should "fix `before` at the first use, not when `before` is first asked for" in {
    val a = ev("a", SourceType.COURSE, "c1", 1000L)
    val t = new Table(List(a))
    val snap = new LedgerSnapshot("u1", t.read())
    snap.load()
    t.rows = t.rows :+ ev("b", SourceType.COURSE, "c2", 2000L) // the write lands in the table
    snap.before shouldBe List(a)
  }

  it should "keep the rows it was read with as `before`, untouched by later writes" in {
    val a = ev("a", SourceType.COURSE, "c1", 1000L)
    val snap = new LedgerSnapshot("u1", List(a))
    snap.added(ev("b", SourceType.COURSE, "c2", 2000L))
    snap.revoked("a", a.evidenceId, "wrong mapping")
    snap.before shouldBe List(a)
  }

  it should "show an appended row in `current` and in its skill" in {
    val snap = new LedgerSnapshot("u1", Nil)
    val b = ev("b", SourceType.ASSESSMENT, "qs1", 2000L)
    snap.added(b)
    snap.current shouldBe List(b)
    snap.ofSkill("b") shouldBe List(b)
    snap.ofSkill("a") shouldBe Nil
  }

  it should "mark a revoked row in place, with its reason" in {
    val a = ev("a", SourceType.COURSE, "c1", 1000L)
    val snap = new LedgerSnapshot("u1", List(a))
    snap.revoked("a", a.evidenceId, "wrong mapping")
    snap.ofSkill("a").map(e => (e.revoked, e.revokedReason)) shouldBe List((true, Some("wrong mapping")))
  }

  it should "ignore a revocation of a row it does not hold" in {
    val a = ev("a", SourceType.COURSE, "c1", 1000L)
    val snap = new LedgerSnapshot("u1", List(a))
    snap.revoked("a", "no-such-id", "x")
    snap.current shouldBe List(a)
  }

  // The ledger's duplicate check reads the snapshot, so a second credit within one write
  // (reproject walking two enrolments that teach the same skill) is caught without a re-read.
  it should "let the duplicate check see a row appended earlier in the same write" in {
    val snap = new LedgerSnapshot("u1", Nil)
    snap.added(ev("a", SourceType.ASSESSMENT, "qs1", 1000L))
    val existing = snap.ofSkill("a").map(x => (x.sourceType, x.sourceId, x.batchId, x.revoked))
    AttainmentRules.isDuplicate(existing, SourceType.ASSESSMENT, "qs1", "b1") shouldBe true
  }

  it should "move the profile from not held to held, and back on revocation" in {
    val snap = new LedgerSnapshot("u1", Nil)
    val a = ev("a", SourceType.COURSE, "c1", 1000L)
    snap.added(a)
    AttainmentRules.profile(snap.before).keySet shouldBe Set.empty[String]
    AttainmentRules.profile(snap.current).keySet shouldBe Set("a")
    snap.revoked("a", a.evidenceId, "x")
    AttainmentRules.profile(snap.current).keySet shouldBe Set.empty[String]
  }
}
