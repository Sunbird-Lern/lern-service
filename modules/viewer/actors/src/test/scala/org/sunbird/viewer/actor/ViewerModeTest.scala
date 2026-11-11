package org.sunbird.viewer.actor

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

// Phase 0: the mode-aware consumption-key resolver. resolveKey returns (collectionid, contextid);
// contentid is always the content and is handled by the caller. strict reproduces today's viewKey cascade.
class ViewerModeTest extends AnyFlatSpec with Matchers {

  import ViewerMode.resolveKey

  // --- strict: reproduces the existing viewKey cascade (ViewConsumptionActor 318-320) ---
  "strict" should "key organic content at (content, content)" in {
    resolveKey("strict", "content", None, None, "leaf1") shouldBe (("leaf1", "leaf1"))
  }

  it should "key collection-only at (collection, collection)" in {
    resolveKey("strict", "content", Some("C"), None, "leaf1") shouldBe (("C", "C"))
  }

  it should "key in-context at (collection, context)" in {
    resolveKey("strict", "content", Some("C"), Some("B"), "leaf1") shouldBe (("C", "B"))
  }

  // --- noContext content: always collapses to the content, even inside a context ---
  "noContext content" should "always key at (content, content)" in {
    resolveKey("noContext", "content", Some("C"), Some("B"), "leaf1") shouldBe (("leaf1", "leaf1"))
    resolveKey("noContext", "content", None, None, "leaf1") shouldBe (("leaf1", "leaf1"))
  }

  // --- noContext collection: collapses the context to the collection ---
  "noContext collection" should "key in-context at (collection, collection)" in {
    resolveKey("noContext", "collection", Some("C"), Some("B"), "leaf1") shouldBe (("C", "C"))
  }

  it should "fall back to (content, content) when there is no collection (organic)" in {
    resolveKey("noContext", "collection", None, None, "leaf1") shouldBe (("leaf1", "leaf1"))
  }

  // --- copy: strict base; the carry-forward overlay is handled elsewhere (Phase 5) ---
  "copy" should "resolve identically to strict" in {
    resolveKey("copy", "content", Some("C"), Some("B"), "leaf1") shouldBe (("C", "B"))
    resolveKey("copy", "collection", None, None, "leaf1") shouldBe (("leaf1", "leaf1"))
  }

  // --- defaulting: blank / null / unknown mode falls back to strict ---
  "a blank or null mode" should "behave as strict" in {
    resolveKey("", "content", Some("C"), Some("B"), "leaf1") shouldBe (("C", "B"))
    resolveKey(null, "content", Some("C"), Some("B"), "leaf1") shouldBe (("C", "B"))
    resolveKey("   ", "content", Some("C"), None, "leaf1") shouldBe (("C", "C"))
  }

  it should "treat an unknown mode as strict" in {
    resolveKey("weird", "content", Some("C"), Some("B"), "leaf1") shouldBe (("C", "B"))
  }

  // --- normalize: the config-defaulting helper (trim, blank/null -> default) ---
  "normalize" should "trim and fall back to the default for blank or null" in {
    ViewerMode.normalize("noContext", "strict") shouldBe "noContext"
    ViewerMode.normalize("  copy  ", "strict") shouldBe "copy"
    ViewerMode.normalize("", "strict") shouldBe "strict"
    ViewerMode.normalize(null, "strict") shouldBe "strict"
    ViewerMode.normalize("   ", "content") shouldBe "content"
  }
}
