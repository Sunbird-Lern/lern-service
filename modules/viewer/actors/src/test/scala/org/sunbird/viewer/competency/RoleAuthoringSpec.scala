package org.sunbird.viewer.competency

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class RoleAuthoringSpec extends AnyFlatSpec with Matchers {

  import RoleAuthoring._

  private val leaves = Set("dosage-calculation", "iv-administration", "hand-hygiene",
    "ppe-use", "hmis-reporting")

  "diff" should "add every requirement for a role that does not exist yet" in {
    val d = diff("staff-nurse-icu", Set("dosage-calculation", "ppe-use"), Set.empty, leaves)
    d.added shouldBe Set("dosage-calculation", "ppe-use")
    d.removed shouldBe empty
    d.unchanged shouldBe empty
    d.changed shouldBe true
  }

  // The whole point of G3: a blind upsert would make un-ticking a box a no-op, so a role could only
  // ever grow and would drift silently from the matrix it was authored in.
  it should "REMOVE a requirement the authored set no longer names" in {
    val d = diff("r1", Set("dosage-calculation"), Set("dosage-calculation", "ppe-use"), leaves)
    d.removed shouldBe Set("ppe-use")
    d.added shouldBe empty
    d.unchanged shouldBe Set("dosage-calculation")
    d.changed shouldBe true
  }

  it should "report no change when the authored set already matches" in {
    val same = Set("dosage-calculation", "ppe-use")
    val d = diff("r1", same, same, leaves)
    d.added shouldBe empty
    d.removed shouldBe empty
    d.unchanged shouldBe same
    d.changed shouldBe false
  }

  it should "handle add and remove in one pass" in {
    val d = diff("r1", Set("dosage-calculation", "hand-hygiene"),
      Set("dosage-calculation", "ppe-use"), leaves)
    d.added shouldBe Set("hand-hygiene")
    d.removed shouldBe Set("ppe-use")
    d.unchanged shouldBe Set("dosage-calculation")
  }

  // An interior term is never tagged on content, so no evidence path exists and it can never be
  // held. Crediting it would report a learner ready for a role they cannot finish.
  it should "reject a requirement naming an interior term rather than applying it" in {
    val d = diff("r1", Set("dosage-calculation", "medication-administration"), Set.empty, leaves)
    d.added shouldBe Set("dosage-calculation")
    d.rejected shouldBe Set("medication-administration")
  }

  it should "reject a code absent from the tree, such as a typo" in {
    val d = diff("r1", Set("dosage-calculaton"), Set.empty, leaves)
    d.added shouldBe empty
    d.rejected shouldBe Set("dosage-calculaton")
  }

  // A rejected code must never count as a removal: the author did not un-tick it, they mistyped it.
  it should "not remove an existing requirement merely because another entry was rejected" in {
    val d = diff("r1", Set("dosage-calculation", "bogus"), Set("dosage-calculation"), leaves)
    d.removed shouldBe empty
    d.unchanged shouldBe Set("dosage-calculation")
    d.rejected shouldBe Set("bogus")
  }

  // Authoring a role down to nothing is a real edit, not a no-op: it must clear the requirements.
  it should "remove everything when the authored set is empty" in {
    val d = diff("r1", Set.empty, Set("dosage-calculation", "ppe-use"), leaves)
    d.removed shouldBe Set("dosage-calculation", "ppe-use")
    d.changed shouldBe true
  }

  it should "report nothing for an empty role that is already empty" in {
    diff("r1", Set.empty, Set.empty, leaves).changed shouldBe false
  }

  // Fail-closed on an unresolved framework is handled in the service; here the tree is simply empty.
  it should "reject every requirement when the tree has no leaves" in {
    val d = diff("r1", Set("dosage-calculation"), Set.empty, Set.empty)
    d.added shouldBe empty
    d.rejected shouldBe Set("dosage-calculation")
  }

  "nextVersion" should "bump on a real change" in {
    val d = diff("r1", Set("ppe-use"), Set.empty, leaves)
    nextVersion(3, d) shouldBe 4
  }

  // Re-applying an unchanged matrix must not invalidate a learning path pinned to a version.
  it should "hold steady when nothing changed" in {
    val same = Set("ppe-use")
    nextVersion(3, diff("r1", same, same, leaves)) shouldBe 3
  }

  it should "start at 1 for a role that did not exist" in {
    nextVersion(0, diff("r1", Set("ppe-use"), Set.empty, leaves)) shouldBe 1
  }

  // A rejection alone is not a change: nothing was written, so the version must not move.
  it should "not bump for a rejection alone" in {
    nextVersion(2, diff("r1", Set("bogus"), Set.empty, leaves)) shouldBe 2
  }
}
