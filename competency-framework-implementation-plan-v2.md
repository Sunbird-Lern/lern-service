# Competency framework — implementation plan

## Summary

Seven phases turning the competency design into working code. The learner-side layer already exists
on this branch as a level-based implementation; phase 3 rewrites it in place against the new model.
Phases 1, 2 and 6 are new work, and phase 1 lands outside this repository.

## Highlights

| Item | Position |
|---|---|
| Already landed | The Learning Path prerequisites — persisted `optional_nodes` and `optionality_computed`, single-writer root enrolment row, `advance(completedNow)` |
| Rewritten in place | `org.sunbird.viewer.competency` — 8 files, 1135 lines. No file added, none removed |
| Naming rule | Files and service classes keep their `Competency*` names. Data classes, tables and routes take the design's skill vocabulary |
| Critical path | Phase 1 (Knowlg enum) → phase 3 (ledger and profile) → phases 4 and 5 |
| Largest single risk | `CompetencyFrameworkUtil.meta` returns `CompetencyMeta.empty` when a framework has no `proficiencylevel` terms, so the new frameworks silently no-op until phase 3 lands |
| Most expensive work | Retagging the published catalogue with `skills` — not code |
| Blocked on a decision | Phases 3 and 6. See below |

## Open decisions

Four of these block a phase. Each carries a leaning, not a conclusion.

| # | Decision | Blocks | Leaning |
|---|---|---|---|
| D1 | `CompetencyLedger.creditAssessments` takes `attempts.maxBy(_.totalScore)`. With binary attainment, a skill answered at full marks in attempt 1 goes uncredited if attempt 2 scored higher overall but got that skill wrong | Phase 3 | Union over all attempts: a skill is held if **any** attempt answered every question tagged with it at full marks. Matches "nothing decrements" and removes the need to pick a best attempt at all |
| D2 | The ledger currently appends sub-threshold evidence at `levelIndex = 0` so the profile can show `IN_PROGRESS`. Under "held = a live row exists" that would credit every attempted skill | Phase 3 | Append no row for a skill that was not earned. Drop `IN_PROGRESS`. "Attempted but not held" is already answerable from `assessment_aggregator` |
| D3 | Classes keep `Competency*` names while tables become `user_skill*` and routes `/v1/skill/*`. The two vocabularies then disagree in the same file | Nothing | Follow the design for tables and routes. Both are being dropped and recreated, and no client consumes the routes yet. Reversible either way |
| D4 | `modules/lms` and `modules/viewer` are separate Maven profiles, so lms cannot reference the skill layer in-JVM. Private LP creation needs the gap to seed itself | Phase 6 | Put the private LP APIs in lms and have them call `/v1/skill/gap/read` over HTTP, the trade `EnrolDispatcher` already makes. Alternative is a viewer-side endpoint that has no collection or batch machinery |
| D5 | `recommend` resolves the role as current, else the first target. The design computes a gap against both | Phase 5 | Return both gaps and let the client choose. One extra partition read |
| D6 | `claimsOf` searches content with `fields = ["competencies"]`. Every Live course and question needs `skills` instead | Phase 3 having real data | Undecided. A bulk metadata update on Live content means a republish per course |

## Phase 1 · Vocabulary

Knowlg, not this repository. Nothing in lern-service compiles against it, but phase 3 cannot be
tested without it.

| Task | Where | Change |
|---|---|---|
| Add the framework type | `schemas/framework/1.0/schema.json` | `type` enum gains `"Competency"`; today `["K-12", "TPD"]` |
| Master categories | Framework config | Two — `competency`, `role`. `competencyarea`, `proficiencylevel`, `position` and `competencyrequirement` are not created |
| Custom framework property | `schemas/framework/1.0/config.json` | `tierLabels` |
| Content and collection fields | Content schema | `skills: array<text>`; `targetRole: text` on the collection — nothing reads a collection-level target today. `competencyFramework` is already read by `CompetencyFrameworkUtil.frameworkOf` |
| Question field | Question schema | `skills: array<text>` |
| Author one framework | Data | `fw_health_competency`, three tiers, two roles, published |

**Done when** `GET /v1/framework/read/fw_health_competency` returns a `competency` category whose
terms nest by `children`, and a `role` category whose terms carry associations to leaf terms.

**Verify first.** Whether custom term and framework properties survive create and publish is the
open question the whole plan rests on. Confirm `tierLabels` and the role associations round-trip
before writing any code.

## Phase 2 · Spec engine

A CLI against the public framework and term APIs. No lern-service change.

| Command | Behaviour |
|---|---|
| `validate` | Spec parses; tree is at least three levels deep; codes unique across the tree; every code in `roles[].skills` resolves to a leaf |
| `plan` | Diff against the live framework. Fails on any code rename. Absence from `tree` is ignored, never a retirement |
| `apply` | Idempotent and resumable. Re-running against an unchanged spec writes nothing |
| `publish` | Framework publish, then `POST /private/v1/skill/cache/invalidate` |

Three front-ends — workbook, YAML in git, LLM draft from the source document — all emitting the one
spec. Build the YAML front-end first; it is the one a test can drive.

**Done when** `apply` twice in a row produces one framework and an empty second diff.

## Phase 3 · Ledger and skill profile

The bulk of the work, and the only phase that touches shipped code. All paths relative to
`modules/viewer/actors/src/main/scala/org/sunbird/viewer/`.

### The blocker to clear first

`CompetencyFrameworkUtil.meta` returns `CompetencyMeta.empty` when `parseLevels` finds no
`proficiencylevel` terms, and `CompetencyMeta.isEmpty` is `levels.isEmpty && claimsByNode.isEmpty`.
`CompetencyService.onNodeCompleted` and `onAssessed` both early-return on `isEmpty`. A new-model
framework therefore resolves to empty and the layer does nothing, silently. Redefine `isEmpty` as
"the tree resolved no leaves" before anything else in this phase.

### Files

| File | Change |
|---|---|
| `competency/CompetencyModels.scala` | Delete `LevelDef`, `RequirementDef`, `Criticality`. `CompetencyClaim` collapses to a bare code, so `claimsByNode` becomes `Map[String, List[String]]`. `CompetencyMeta` swaps the scale and validity fields for `leaves: Set[String]`, `tierLabels: List[String]`, `roleSkills: Map[String, Set[String]]`. `Evidence` drops `level`, `levelIndex`, `evidenceCount`, `expiresOn`. `PassbookEntry` becomes `SkillEntry` — no level, no status, no expiry. `GapRow` becomes `(skillId, MET \| MISSING)`. `PositionAssignment` becomes `RoleAssignment` |
| `competency/AttainmentRules.scala` | Delete `band`, `pct`, `capCompletion`, `isExpired`, `expiryOf`, `expiryBucket`, `IN_PROGRESS`, `EXPIRING`, `EXPIRED`, `best`. `project` returns `Option[SkillEntry]` — `Some` when a non-revoked row exists, `None` otherwise. Keep `evidenceId` unchanged; it already sorts and dedupes. New: `fullMarks(questions)` |
| `competency/CompetencyFrameworkUtil.scala` | Delete `CAT_AREA`, `CAT_LEVEL`, `CAT_REQUIREMENT`, `parseLevels`, `parseValidity`, `validityMonthsFor`, `defaultRequiredLevel` and `maxCompletionDerivedLevel` handling. `CAT_POSITION` becomes `CAT_ROLE = "role"`. New `parseTree` walks `children` to produce the leaf set and the tier depth. `parseRequirements` keeps only its small-variant branch — a role term's own associations to `competency` terms — and returns `Set[String]` per role. `parseClaims` reads `skills` as a flat array. `searchByIds` requests `skills` |
| `competency/CompetencyLedger.scala` | `creditCompletion` drops the cap and the expiry stamp; it appends one row per code in `skills`. `creditAssessments` replaces cut-score banding with the full-marks test, appends only for skills that pass it (D2), and unions across attempts (D1). `importExternal` loses its level argument |
| `competency/CompetencyProjector.scala` | Delete `sweep`, `bucketsAround` and the `expiringWindowMillis` config read. `project` drops the `indexExpiry` write; the `None` branch deletes the row instead of calling `setPassbookStatus`. `reproject` and `rederive` unchanged in shape |
| `competency/CompetencyDao.scala` | Three tables, not five. Delete `indexExpiry`, `expiriesIn`, `setPassbookStatus`, `upsertRequirement`. Rename `passbookOf`/`upsertPassbook` to `profileOf`/`upsertSkill`, `positionOf`/`upsertPosition` to `roleOf`/`upsertRole`. New `deleteSkill`. Keep `enrolmentsOf` and `assessedContentIds` as they are |
| `competency/CompetencyService.scala` | `heldLevels` becomes `heldSkills(userId): Set[String]`. `claimIndexes` becomes `claimsOf(nodeIds): Map[String, List[String]]`. Delete `sweep` and `refreshRequirements`. `gap` returns `(List[GapRow], Int)` over set difference |
| `competency/GapCalculator.scala` | `rows` becomes `required.toList.map(s => GapRow(s, if held MET else MISSING))`. `readiness` counts MET over required. Delete `isMandatory` |
| `resources/competency.cql` | Replace `user_competency_evidence`, `user_competency` and `user_competency_position` with `user_skill_evidence`, `user_skill` and `user_role` per the design. Delete `competency_expiry_index` and `competency_requirement`. Keep both `user_enrolments` ALTERs — they are already applied |
| `actor/CompetencyActor.scala` | Delete the `expirySweep` case and handler. Rename `passbookRead` to `profileRead`. `gapRow` emits `skillId` and `status` only. `frameworkRead` returns the tree, tier labels and role skill sets in place of the scale and requirements |
| `../service/app/controllers/viewer/CompetencyController.java` | Delete `expirySweep`. Rename `passbookRead` to `profileRead`, `positionUpdate` to `roleUpdate` |
| `../service/conf/routes` | Replace the two route blocks with the design's ten routes. `/private/v1/competency/expiry/sweep` goes |

### Tests

| File | Change |
|---|---|
| `competency/AttainmentRulesSpec.scala` | Rewrite. Drop every banding and expiry case. New: full marks across one and several questions, one wrong answer, a revoked row, all rows revoked |
| `competency/CompetencyFrameworkUtilParseSpec.scala` | Rewrite. New: leaf resolution at three and four tiers, uneven branch depth, a role associating a non-leaf, `skills` parsed as a flat array |
| `competency/GapCalculatorSpec.scala` | Rewrite as set difference. Readiness at 0, part and 100 |
| `competency/CompetencyLedgerSpec.scala` | The one file added anywhere in this plan. The full-marks and union-across-attempts rules are the two most arguable in the design and neither is covered today |

**Done when** a learner completing a tagged course, and a learner answering a skill's questions at
full marks, both end with exactly one `user_skill` row; a partially correct skill has none; and
`reproject` reproduces both from an empty projection.

## Phase 4 · Skill-aware waiving

| File | Change |
|---|---|
| `util/ProgressionPolicy.scala` | `computeOptionalNodes` takes `claimsByCourse: Map[String, List[String]]` and `held: Set[String]`. The predicate becomes `claims.nonEmpty && claims.forall(held.contains)`. The `required > 0` guard goes with the levels |
| `engine/LpProgressionEngine.scala` | Six call sites. Lines 88–101 and 135–148: `heldLevels` becomes `heldSkills`, `claimIndexes` becomes `claimsOf`. The Adaptive-with-no-framework halt at line 129 stays |
| `util/ProgressionPolicySpec.scala` | Rewrite the waiver cases as set-subset. Add the case the design leans on: a course teaching three leaves with two held is **not** waived |

**Done when** a course is waived only if every leaf it teaches is held, and an assessment course is
never waived.

## Phase 5 · Roles, gap and recommendation

| Task | Where | Change |
|---|---|---|
| Role assignment | `CompetencyDao`, `CompetencyActor` | `user_role` read and write. `source` stays `HRMS \| PROFILE \| SELF` |
| Gap and readiness | `GapCalculator`, `CompetencyService` | Already rewritten in phase 3; wire `gap/read` to return current and target (D5) |
| Recommendation | `CompetencyActor.recommend` | Ranks candidates by gap skills covered, then by effort after waiving. Today it returns bare outstanding codes and no candidates |
| Coverage report | LP publish | Role skills against the union of its courses' `skills`. Warns on assessability. Does not block publish |

**Done when** readiness against a role moves as skills are earned, and `recommend` returns ranked
collections rather than a code list.

## Phase 6 · Private learning paths

No existing implementation. Depends on D4.

| Task | Where |
|---|---|
| `/v1/lp/private/create`, `update`, `read`, `delete` | `modules/lms`, alongside `CourseBatchManagementActor` |
| Learner-owned collection | `owner = userid`, `visibility = PRIVATE`, no publish, no review |
| Seed from the gap | HTTP call to `/v1/skill/gap/read`, then resolve covering courses |
| Implicit personal batch | One batch per private LP, created on first enrolment |
| Policy | Forced to `PriorLearning` |
| Certificates | Course certificates and skill badges issue. No LP certificate |

**Done when** a learner creates a path from their gap, works through it, earns course certificates
and skill badges, and gets no programme certificate.

## Phase 7 · Import, revoke and badges

| Task | Where |
|---|---|
| `evidence/import` and `evidence/revoke` | Already present in `CompetencyActor`; drop the level argument |
| Skill badge issue on attain | Certificate service. Triggered from the projector's write path |
| Badge revoke on evidence revoke | Certificate service |
| `skills` block on the certificate template | `course_batch` template, additive |

**Done when** revoking an evidence row removes the skill from the profile and revokes its badge.

## Sequencing

| Phase | Depends on | Can start before its dependency lands |
|---|---|---|
| 1 · Vocabulary | — | — |
| 2 · Spec engine | 1 | No. It writes to the schema phase 1 adds |
| 3 · Ledger and profile | 1 | Yes, against a hand-built framework fixture |
| 4 · Waiving | 3 | No |
| 5 · Roles and gap | 3 | Partly. `GapCalculator` is pure and lands in phase 3 |
| 6 · Private LPs | 5, D4 | Yes, the CRUD half |
| 7 · Badges | 3 | Yes |

Phases 3 and 6 are the two that need a full sprint each. Phases 4 and 7 are days.

## Risks

| Risk | Consequence | Mitigation |
|---|---|---|
| Custom framework and term properties do not survive Knowlg publish | Phase 1 fails and the model has no home | Verify before writing code. A schema config change per property is the expected fix |
| Silent no-op on an unresolvable framework | A learner completes a path and earns nothing, with only a `warn` in the log | Redefine `isEmpty` first. Add batch-creation validation of `competencyFramework` — still blocked by D4 |
| Full marks is a hard bar | A single badly written question keeps a skill permanently unheld | Report per-skill attainment rates in the coverage panel so a bad question is visible |
| Leaf tagging makes waiving stricter | Learners sit through courses they largely know | Intended. Watch the waive rate per LP after phase 4 |
| Retagging the Live catalogue | Phase 3 lands with no data to act on | D6. Sequence the tagging effort to start during phase 1 |
| Tables dropped, not migrated | Any evidence written by the shipped layer is lost | The layer is unreleased. Confirm no environment holds real `user_competency_evidence` rows before dropping |

## Verification

| Level | What |
|---|---|
| Unit | `AttainmentRules`, `GapCalculator` and `ProgressionPolicy` stay pure and cover every rule that could be argued about — full marks, union across attempts, set difference, set-subset waiving |
| Parse | `CompetencyFrameworkUtilParseSpec` against real `/v1/framework/read` and `/v3/search` payloads, including a four-tier tree and an uneven branch |
| Integration | One learner through the ICU induction path in the design: 8 required skills, 1 imported, 2 courses waived, 1 not waived, readiness 13% → 100% |
| Idempotency | Replay the same completion and the same aggregate event; the row count must not move |
| Rebuild | Truncate `user_skill`, run `reproject`, and diff against the pre-truncation state |
