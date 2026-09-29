package org.sunbird.viewer.actor

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import org.sunbird.viewer.actor.CarryForwardRules.{Candidate, Target, passes}

// The carry-forward filter: keep a source completion iff it satisfies the configured rules (empty => fail-closed).
class CarryForwardRulesTest extends AnyFlatSpec with Matchers {

  private val DAY = 86400000L
  private val now = 1_000_000_000_000L
  private def target(start: Option[Long] = None, end: Option[Long] = None) = Target(start, end, None)
  private def done(daysAgo: Long) = Candidate(Some(now - daysAgo * DAY))

  "empty / blank rules" should "carry nothing (fail-closed)" in {
    passes(done(1), target(), now, "") shouldBe false
    passes(done(1), target(), now, "   ") shouldBe false
    passes(done(1), target(), now, null) shouldBe false
  }

  "unparseable rules" should "carry nothing" in {
    passes(done(1), target(), now, "{ not json") shouldBe false
  }

  "always" should "carry unconditionally" in {
    passes(done(9999), target(), now, """{"match":"all","rules":[{"type":"always"}]}""") shouldBe true
  }

  "completedWithin months=3" should "keep a recent completion and drop an old one" in {
    val rules = """{"match":"all","rules":[{"type":"completedWithin","unit":"months","value":3}]}"""
    passes(done(30), target(), now, rules) shouldBe true   // ~1 month ago
    passes(done(120), target(), now, rules) shouldBe false // ~4 months ago
  }

  "withinContextDuration" should "keep a completion inside the batch window and drop one before it" in {
    val rules = """{"match":"all","rules":[{"type":"withinContextDuration"}]}"""
    val batch = target(start = Some(now - 10 * DAY), end = Some(now + 10 * DAY))
    passes(done(5), batch, now, rules) shouldBe true    // 5 days ago, inside [-10, +10]
    passes(done(20), batch, now, rules) shouldBe false  // 20 days ago, before start
  }

  "match=any" should "pass if any rule passes" in {
    val rules = """{"match":"any","rules":[{"type":"completedWithin","unit":"days","value":1},{"type":"always"}]}"""
    passes(done(365), target(), now, rules) shouldBe true // completedWithin fails, always passes
  }

  "match=all" should "fail if any rule fails" in {
    val rules = """{"match":"all","rules":[{"type":"completedWithin","unit":"days","value":1},{"type":"always"}]}"""
    passes(done(365), target(), now, rules) shouldBe false // completedWithin fails => all fails
  }

  "an empty rules array" should "carry nothing" in {
    passes(done(1), target(), now, """{"match":"all","rules":[]}""") shouldBe false
  }
}
