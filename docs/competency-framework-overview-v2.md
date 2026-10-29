# Competency framework — overview

## Summary

A competency framework is a tree of skills that sits next to the existing taxonomy framework.
Content is tagged with the tree's leaf skills. Learners earn those skills as they learn, and the
platform uses what they hold to skip courses they already know and to suggest what to take next.
Roles, the skills a job, grade or career track requires, are kept in lern, not in the framework.

The examples follow a state health department training its nursing staff. [Other use
cases](#other-use-cases) shows the same model for schools and software skills.

## Two frameworks on one course

![One course tagged by a taxonomy framework and a competency framework](images/competency-overview-frameworks-v2.svg)

Both are Knowlg frameworks, created with the same framework and term APIs.

The taxonomy framework answers *where does this content belong?* Its categories, such as
department, specialty, language and topic in the health example, drive the catalogue, search and
filters. It is unchanged.

The competency framework answers *what does this content teach?* It is marked with framework type
`Competency`. A course points at it through `competencyFramework` and lists its skills in `skill`.

## The skill tree

![A three-tier skill tree with leaf skills, a tagged course and a role](images/competency-overview-tree-v2.svg)

The tree is one `competency` category whose terms nest by `children`. It has at least three tiers
and no upper limit. Tier names such as *Competency area*, *Competency* and *Skill* are display
labels, set on the framework as `tierLabels`. Branches may stop at different depths.

The leaves are the skills. Only leaves are tagged on courses and questions, required by roles and
held by learners. Higher tiers exist for navigation and reporting.

## Roles

![How a role links skills and learners: framework, role_skill, user_role, user_skill, gap](images/competency-overview-roles-v2.svg)

A role is a named set of leaf skills: a job post, a grade or a career track. It is not part of the
Knowlg framework. lern stores it in `role_skill`, one row per role and required skill, and checks
against the framework that every skill is a leaf when the role is saved. Admins author roles with
`POST /v1/competency/role/upsert` or `/role/import`. Roles are retired, never deleted.

A learner's roles are in `user_role`. An admin sets the role the learner holds today
(`assigned_role`, through `/v1/competency/role/assign`). The learner picks the roles they are aiming
for (`target_roles`, through `/v1/skill/role/update`). Earning a skill never touches roles: evidence
is recorded per skill.

Readiness is computed on each request. For every role in `user_role`, the skills in `role_skill` are
compared with the skills the learner holds in `user_skill_evidence`. A required skill is held or not held; there are no
proficiency levels. An app can also pass `role` to ask about any other role.

Example: Asha is assigned `staff-nurse-icu`, which requires 8 skills, and targets
`nursing-officer`. `POST /v1/skill/gap/read` returns her readiness for both. The *ICU Nurse
Induction* path declares `targetRole = staff-nurse-icu`, but that is for authors only:
`/v1/skill/coverage/read` checks that the path's courses teach every skill the role requires.

## How it works

![End-to-end flow: set up the framework and content, then the learner loop](images/competency-overview-flow-v2.svg)

Steps 1 to 5 are set-up: the framework in Knowlg, roles and role assignments in lern, then content
and Learning Paths. The learner loop runs automatically each time a learner finishes something.

A skill is binary: held or not held. There are no proficiency levels and no expiry.

| Source | A skill is recorded when |
|---|---|
| `COURSE` | A course tagged with the skill is completed |
| `ASSESSMENT` | Every question tagged with the skill scores full marks in an attempt |
| `LEARNING_PATH` | A Learning Path tagged with the skill is completed |
| `EXTERNAL` (optional) | An admin imports a credential earned outside the platform. Uses an internal admin API that the deployed `lern` service does not expose yet |

The profile drives four things:

- **Waiving.** A course in a Learning Path is skipped when the learner already holds every skill it
  teaches. A course with no skills is skipped only if it was completed before.
- **Readiness.** The share of a role's skills the learner holds, for each assigned and target
  role. See [Roles](#roles).
- **Recommendation.** Courses and paths that cover the skills still missing.
- **Recognition.** Course and Learning Path certificates, plus one skill badge per skill held.

**Example.** Asha is a staff nurse in ICU, a role that needs 8 skills. She starts with 1, imported
from an external resuscitation card (the optional `EXTERNAL` source). An entry assessment proves 3 more. Of the induction path's
four courses, two are skipped because she already holds their skills. She completes the other two and
passes the outcome assessment, finishing with all 8.

## Other use cases

The model, tables, APIs and rules stay the same. Only the tree's depth and what a role means change.

### School education

![School use case: a four-tier tree with a grade as the role](images/competency-overview-school-v2.svg)

Learning outcomes break down one step further than clinical skills, so the tree has four tiers. A
role is a grade, stored in `role_skill` like any other role. The taxonomy's `gradeLevel` still
classifies content; the role lists the skills a grade requires. A child entering Grade 7 who
already holds the Grade 6 skills skips the courses that teach them.

### Software skills

![Software use case: a three-tier tree with a career track as the role](images/competency-overview-software-v2.svg)

A role is a career track the learner chooses. Leaves are written as actions, which makes each
skill badge meaningful to an employer.

## Database changes

All tables are in the `sunbird_courses` keyspace. The schema is in
`modules/viewer/actors/src/main/resources/competency.cql`.

| Table | Change | Holds | Key |
|---|---|---|---|
| `user_skill_evidence` | New | Every reason a learner holds a skill: source, batch, score, date, revoked flag. The skill profile is computed from it | `userid`, `skillid`, `evidenceid` |
| `role_skill` | New | What each role requires: one row per role and leaf skill, with name, status and version | `framework_id`, `roleid`, `skillid` |
| `user_role` | New | A learner's framework, `assigned_role` and `target_roles` | `userid` |
| `user_enrolments` | Two columns added | `optional_nodes`, `optionality_computed`: which courses were waived | - |

Evidence rows are never deleted; revoking one sets a flag. A skill is held while it has at least
one row that is not revoked. There is no separate profile table. Roles are only read when readiness is computed.

The schema is applied locally by `scripts/init-yugabyte.sh` and must be added to the deployment
migrations before release.

Knowlg needs these schema changes:

| Schema | Change |
|---|---|
| Framework | `Competency` added to the `type` values; `tierLabels` property |
| Content and collection | `competencyFramework`, `skill`; `targetRole` on Learning Paths |
| Question | `skill` |

## Rules

- Only leaf skills are tagged or held. A non-leaf skill has no way to be earned.
- A role requires leaf skills only. Anything else is rejected when the role is saved.
- Roles are retired, never deleted, because learner assignments and Learning Paths hold the role
  code.
- Skill codes never change. A rename changes the label only.
- Terms are retired, never deleted, because evidence points at them.
- A collection uses one competency framework, so a skill code is never ambiguous.

## Open questions

- Who feeds `assigned_role` in production: an HR system, or admins by hand?
- Should a learner's `target_roles` be checked against `role_skill` when saved?
- Who may see another learner's skill profile?
- Should skills expire for regulated professions?
- How is already-published content tagged with skills?
