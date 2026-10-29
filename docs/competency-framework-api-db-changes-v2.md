# Competency Framework — API & DB Changes

Branch: `feat/competency-framework-design`. Controller: `CompetencyController` → `CompetencyActor` (`/competency-actor`).

## New API Endpoints

### Learner (user id taken from the token)

| Method | Path | Purpose |
|---|---|---|
| POST | `/v1/skill/profile/read` | Learner's held skills |
| POST | `/v1/skill/gap/read` | Gap between held skills and assigned/target role |
| POST | `/v1/skill/recommend` | Content recommended to close the gap |
| POST | `/v1/skill/role/update` | Learner sets own target roles |
| POST | `/v1/skill/coverage/read` | Skill coverage of a programme (authoring check) |
| GET | `/v1/competency/framework/read/:frameworkId` | Framework leaf skills, tier labels and role → skill map |

### Role authoring (admin only — checked in controller against the user token)

| Method | Path | Purpose |
|---|---|---|
| POST | `/v1/competency/role/read` | Roles as authored, including retired |
| POST | `/v1/competency/role/upsert` | Replace one role's full skill set (`reactivate: true` brings back a retired role) |
| POST | `/v1/competency/role/retire` | Retire a role (never deleted) |
| POST | `/v1/competency/role/import` | Bulk import of the role × skill matrix (`dryRun` supported) |
| POST | `/v1/competency/role/assign` | Assign a learner's current role |

### Optional — internal admin and operations (viewer module only, not exposed in the `lern` runtime)

Not needed for the creation or learner flow. Used only for the cases below.

| Method | Path | Purpose | Needed when |
|---|---|---|---|
| POST | `/private/v1/skill/role/update` | Set any learner's assigned/target roles | An HR system feeds role assignments |
| POST | `/private/v1/skill/evidence/import` | Import external evidence | Skills earned outside the platform are credited |
| POST | `/private/v1/skill/evidence/revoke` | Revoke an evidence row | A recorded skill must be withdrawn |
| POST | `/private/v1/skill/reproject` | Re-derive a learner's evidence from their enrolments and attempts | A course was retagged with skills after learners completed it |
| POST | `/private/v1/competency/cache/invalidate` | Drop cached framework metadata | A framework republish must show before the 1-hour cache expires |

Learner and role-authoring routes are declared in both `modules/viewer/service/conf/routes` and `modules/lern/service/conf/routes`.

## DB Changes

Keyspace: `sunbird_courses`. Schema file: `modules/viewer/actors/src/main/resources/competency.cql` (applied locally by `scripts/init-yugabyte.sh`; still to be ported to `sunbird-spark-installer`).

### New tables

| Table | Primary key | Holds |
|---|---|---|
| `user_skill_evidence` | `((userid), skillid, evidenceid)` | Append-only evidence ledger (course, assessment, learning path, external). Only `revoked` is updated. A skill is held while it has a non-revoked row; the profile is computed from this table on read |
| `role_skill` | `(framework_id, roleid, skillid)` | Role → required leaf skills (source of truth); `role_name`, `status`, `version` |
| `user_role` | `(userid)` | Learner's `assigned_role` and `target_roles` per framework |

### Altered tables

| Table | Change |
|---|---|
| `user_enrolments` | `ADD optional_nodes set<text>`, `ADD optionality_computed boolean` |

`optional_nodes` already exists on `main` (Learning Path work); the script re-states both columns so a fresh environment gets them in one pass.
