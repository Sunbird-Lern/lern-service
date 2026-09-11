// Competency framework — learner-side schema.
// Keyspace matches `sunbird_course_keyspace` (application.conf), i.e. the keyspace that already
// holds user_enrolments. Port these statements into sunbird-spark-installer
// (scripts/sunbird-yugabyte-migrations/sunbird-lern/sunbird_courses.cql) before any deployment;
// scripts/init-yugabyte.sh applies this file for local development.
//
// A skill is held or not held. There is no proficiency scale, so no level or level_index column,
// and no expiry, so no expires_on column and no expiry index table.

// --- Prerequisites on the existing Learning Path tables ---------------------------------------
// Already applied. Retained so a fresh environment gets them in one pass.

// Per-learner waived course set on the LP enrolment row.
ALTER TABLE sunbird_courses.user_enrolments ADD optional_nodes set<text>;

// Persisted replacement for the JVM-local optionality latch. Distinguishes "computed, nothing
// waived" from "not yet computed", which an empty set cannot do.
ALTER TABLE sunbird_courses.user_enrolments ADD optionality_computed boolean;

// --- Superseded tables ------------------------------------------------------------------------
// The level-based tables are dropped rather than altered: a level column with no scale behind it
// is worse than no column. Confirm no environment holds real evidence rows before running these.

DROP TABLE IF EXISTS sunbird_courses.user_competency_evidence;
DROP TABLE IF EXISTS sunbird_courses.user_competency;
DROP TABLE IF EXISTS sunbird_courses.user_competency_position;
DROP TABLE IF EXISTS sunbird_courses.competency_expiry_index;
DROP TABLE IF EXISTS sunbird_courses.competency_requirement;

// --- Evidence ledger and skill profile --------------------------------------------------------

// Append-only. The source of truth. Only `revoked` is ever updated.
// evidenceid is "<zero-padded occurred_on millis>:<digest of the source>", which gives both time
// ordering and idempotency: replaying the same completion rewrites the same row.
//
// A row exists only for a skill the learner actually earned. Nothing is written for an attempted
// but unearned skill, because the profile reads "held" as "a live row exists".
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
    PRIMARY KEY ((userid, skillid), evidenceid)
) WITH CLUSTERING ORDER BY (evidenceid DESC);

// Derived projection. Droppable and rebuildable from the ledger at any time.
// One row per skill the learner holds. There is no status column, because held is the only state;
// not held is the absence of a row.
CREATE TABLE IF NOT EXISTS sunbird_courses.user_skill (
    userid                text,
    skillid               text,
    framework_id          text,
    source_type           text,
    governing_evidence_id text,
    attained_on           timestamp,
    updated_on            timestamp,
    PRIMARY KEY (userid, skillid)
);

// --- Role assignment --------------------------------------------------------------------------

// assigned_role is written by an HR feed or derived from the learner's profile; target_roles is
// learner-chosen. A role's skill list is framework data and is cached in memory, not stored here.
CREATE TABLE IF NOT EXISTS sunbird_courses.user_role (
    userid        text,
    framework_id  text,
    assigned_role  text,
    target_roles  set<text>,
    source        text,           // HRMS | PROFILE | SELF
    assigned_on   timestamp,
    PRIMARY KEY (userid)
);
