// Competency framework — learner-side schema.
// Keyspace matches `sunbird_course_keyspace` (application.conf), i.e. the keyspace that already
// holds user_enrolments. Port these statements into sunbird-spark-installer
// (scripts/sunbird-yugabyte-migrations/sunbird-lern/sunbird_courses.cql) before any deployment;
// scripts/init-yugabyte.sh applies this file for local development.
//
// A skill is held or not held: there is no proficiency scale and no expiry.

// --- Prerequisites on the existing Learning Path tables ---------------------------------------
// Already applied. Retained so a fresh environment gets them in one pass.

// Per-learner waived course set on the LP enrolment row.
ALTER TABLE sunbird_courses.user_enrolments ADD optional_nodes set<text>;

// Persisted replacement for the JVM-local optionality latch. Distinguishes "computed, nothing
// waived" from "not yet computed", which an empty set cannot do.
ALTER TABLE sunbird_courses.user_enrolments ADD optionality_computed boolean;

// --- Evidence ledger ---------------------------------------------------------------------------

// Append-only. The only learner skill table: there is no separate profile table. A skill is held
// while at least one non-revoked row exists for it, and the profile entry (framework, source,
// attained date) is read off the earliest such row. Only `revoked` is ever updated.
// evidenceid is "<zero-padded occurred_on millis>:<digest of the source>", which orders a skill's
// rows by time. Completions are deduped on (source_type, source_id, batchid) before insert.
//
// Partitioned on userid alone so a learner's whole profile is one partition read; skillid leads
// the clustering key so one skill's rows are still a single slice.
//
// A row exists only for a skill the learner actually earned. Nothing is written for an attempted
// but unearned skill, because "held" is "a live row exists".
CREATE TABLE IF NOT EXISTS sunbird_courses.user_skill_evidence (
    userid          text,
    skillid         text,
    evidenceid      text,
    framework_id    text,
    source_type     text,          // COURSE | ASSESSMENT | LEARNING_PATH | EXTERNAL
    source_id       text,
    batchid         text,
    score           double,        // null for COURSE and EXTERNAL
    max_score       double,
    issuer_id       text,          // non-null only for EXTERNAL
    note            text,
    occurred_on     timestamp,
    revoked         boolean,
    revoked_reason  text,
    PRIMARY KEY ((userid), skillid, evidenceid)
) WITH CLUSTERING ORDER BY (skillid ASC, evidenceid DESC);

// --- Roles -------------------------------------------------------------------------------------

// A "role" here is a named set of leaf skills. It is deliberately abstract, because it means
// something different per deployment: a job post (Staff Nurse (ICU)), a GRADE (grade-6, codes
// reused from the taxonomy), or a career track (Backend Engineer). Do not narrow the name to
// `job_role` - that reading is wrong for the school education use case.
//
// NOTE the collision: sunbird.role / role_group / user_action_role already mean RBAC permissions
// (PUBLIC, ORG_ADMIN, CONTENT_CREATOR). Unrelated concept, same word. They are disambiguated by
// keyspace - RBAC in `sunbird`, competency in `sunbird_courses` - and no competency code path
// touches the RBAC tables. Worth knowing in review; not worth a deployment-specific name.

// What a role requires. One row per (role, skill) pair - one tick in the authoring matrix.
//
// THIS TABLE IS THE SOURCE OF TRUTH, not a cache. Roles are not held in the Knowlg framework, so
// they can change without republishing it and nothing depends on Knowlg round-tripping custom term
// associations. There is no framework fallback: two authorities on what a role requires would
// drift.
//
// The skill TREE stays in the framework: it is the vocabulary, and still the authority on what a
// leaf is.
//
// Partitioned on framework_id alone so `WHERE framework_id = ?` returns every role in one read -
// that is how CompetencyMeta.roleSkills is built. 500 roles x ~20 skills is 10k rows per partition.
//
// role_name and status are denormalised onto every row of a role: a role exists because it requires
// something, so there is no separate registry table. Renaming or retiring rewrites all of a role's
// rows in one logged batch, which stays in one partition since they share framework_id.
CREATE TABLE IF NOT EXISTS sunbird_courses.role_skill (
    framework_id text,
    roleid       text,
    skillid      text,          // must be a LEAF of the framework's competency tree
    role_name    text,          // identical on every row of a role
    status       text,          // LIVE | RETIRED - roles are retired, never deleted
    version      int,           // counts edits to the requirement set; not a pin
    added_on     timestamp,
    PRIMARY KEY (framework_id, roleid, skillid)
);

// Which role a LEARNER holds or targets. Distinct from role_skill above: that is per-framework
// reference data with no user, this is per-learner assignment. assigned_role is written by an HR feed
// or derived from the learner's profile; target_roles is learner-chosen.
CREATE TABLE IF NOT EXISTS sunbird_courses.user_role (
    userid        text,
    framework_id  text,
    assigned_role text,
    target_roles  set<text>,
    source        text,           // HRMS | PROFILE | SELF
    assigned_on   timestamp,
    PRIMARY KEY (userid)
);
