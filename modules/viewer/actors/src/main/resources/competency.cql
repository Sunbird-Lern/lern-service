// Competency framework — learner-side schema.
// Keyspace matches `sunbird_course_keyspace` (application.conf), i.e. the keyspace that already
// holds user_enrolments. Port these statements into sunbird-spark-installer
// (scripts/sunbird-yugabyte-migrations/sunbird-lern/sunbird_courses.cql) before any deployment;
// scripts/init-yugabyte.sh applies this file for local development.

// --- Step 0: prerequisites on the existing Learning Path tables -------------------------------

// Per-learner waived course set on the LP enrolment row.
ALTER TABLE sunbird_courses.user_enrolments ADD optional_nodes set<text>;

// Persisted replacement for the JVM-local optionality latch. Distinguishes "computed, nothing
// waived" from "not yet computed", which an empty set cannot do.
ALTER TABLE sunbird_courses.user_enrolments ADD optionality_computed boolean;

// --- Step 2: evidence ledger and passbook ----------------------------------------------------

// Append-only. The source of truth. Only `revoked` is ever updated.
// evidenceid is "<zero-padded occurredOn millis>:<digest of the source>", which gives both
// time ordering and idempotency: replaying the same completion rewrites the same row.
CREATE TABLE IF NOT EXISTS sunbird_courses.user_competency_evidence (
    userid          text,
    competencyid    text,
    evidenceid      text,
    framework_id    text,
    level           text,
    level_index     int,
    source_type     text,
    source_id       text,
    batchid         text,
    score           double,
    max_score       double,
    evidence_count  int,
    issuer_id       text,
    note            text,
    occurred_on     timestamp,
    expires_on      timestamp,
    revoked         boolean,
    revoked_reason  text,
    PRIMARY KEY ((userid, competencyid), evidenceid)
) WITH CLUSTERING ORDER BY (evidenceid DESC);

// Derived projection. Droppable and rebuildable from the ledger at any time.
CREATE TABLE IF NOT EXISTS sunbird_courses.user_competency (
    userid                text,
    competencyid          text,
    framework_id          text,
    level                 text,
    level_index           int,
    status                text,
    source_type           text,
    governing_evidence_id text,
    attained_on           timestamp,
    expires_on            timestamp,
    updated_on            timestamp,
    PRIMARY KEY (userid, competencyid)
);

// Bucketed so the expiry sweep reads one or two partitions instead of scanning user_competency.
CREATE TABLE IF NOT EXISTS sunbird_courses.competency_expiry_index (
    expiry_bucket text,
    expires_on    timestamp,
    userid        text,
    competencyid  text,
    PRIMARY KEY (expiry_bucket, expires_on, userid, competencyid)
);

// --- Step 4: position and requirements -------------------------------------------------------

CREATE TABLE IF NOT EXISTS sunbird_courses.user_competency_position (
    userid           text,
    framework_id     text,
    current_position text,
    target_positions set<text>,
    source           text,
    assigned_on      timestamp,
    PRIMARY KEY (userid)
);

// Projection of the framework's competencyrequirement terms. A cache; the framework is the
// source of truth. Refreshed on framework publish or through /private/v1/competency/cache/invalidate.
CREATE TABLE IF NOT EXISTS sunbird_courses.competency_requirement (
    framework_id         text,
    positionid           text,
    competencyid         text,
    required_level       text,
    required_level_index int,
    criticality          text,
    PRIMARY KEY ((framework_id, positionid), competencyid)
);
