/*
 * Viewer test keyspace — CREATE-from-scratch (NOT the prod migration).
 *
 * Purpose: spin up an isolated keyspace with every table the viewer touches, already in the
 * generalised (viewer_enabled=true) shape — collectionid/contextid, optional_nodes, user_skills,
 * assessment_aggregator on collection_id/context_id. Use it to test the viewer without touching the
 * live sunbird_courses. (The prod path is viewer.cql: in-place ALTER ... RENAME on sunbird_courses.)
 *
 * Usage:
 *   1. Pick a keyspace name (default below = sunbird_courses_test). To rename, search/replace
 *      "sunbird_courses_test" throughout this file.
 *   2. Run:  cqlsh -f viewer-test-keyspace.cql        (or ycqlsh for YugabyteDB)
 *   3. Point the service at it:  sunbird_course_keyspace=sunbird_courses_test
 *      (env var or externalresource.properties; read env-first via ProjectUtil.getConfigValue).
 *      Hierarchy tables are separate (hierarchy_store_keyspace) and are NOT created here.
 *
 * Target: YugabyteDB (YCQL). The `WITH transactions = {'enabled':'true'}` clauses are required by
 * YCQL for tables that carry secondary indexes. On apache Cassandra, drop those WITH clauses.
 */

CREATE KEYSPACE IF NOT EXISTS sunbird_courses_test
    WITH replication = {'class': 'SimpleStrategy', 'replication_factor': 1};

/* --- UDT used by assessment_aggregator.question --- */
CREATE TYPE IF NOT EXISTS sunbird_courses_test.question (
    id text,
    assess_ts timestamp,
    max_score double,
    score double,
    type text,
    title text,
    resvalues frozen<list<frozen<map<text, text>>>>,
    params frozen<list<frozen<map<text, text>>>>,
    description text,
    duration decimal
);

/* --- user_content_consumption: per-content view state (viewer ucc). Identity = collection/context. --- */
CREATE TABLE IF NOT EXISTS sunbird_courses_test.user_content_consumption (
    userid text,
    collectionid text,
    contextid text,
    contentid text,
    completedcount int,
    completionpercentage float,
    datetime timestamp,
    last_access_time timestamp,
    last_completed_time timestamp,
    last_updated_time timestamp,
    lastaccesstime text,
    lastcompletedtime text,
    lastupdatedtime text,
    progress int,
    progressdetails text,
    status int,
    viewcount int,
    PRIMARY KEY (userid, collectionid, contextid, contentid)
) WITH CLUSTERING ORDER BY (collectionid ASC, contextid ASC, contentid ASC);

/* --- user_enrolments: per-enrolment progress + per-learner optionality (optional_nodes). --- */
CREATE TABLE IF NOT EXISTS sunbird_courses_test.user_enrolments (
    userid text,
    collectionid text,
    contextid text,
    active boolean,
    addedby text,
    certificates list<frozen<map<text, text>>>,
    certstatus int,
    completedon timestamp,
    completionpercentage int,
    contentstatus map<text, int>,
    datetime timestamp,
    enrolled_date timestamp,
    enrolleddate text,
    issued_certificates list<frozen<map<text, text>>>,
    lastcontentaccesstime timestamp,
    lastreadcontentid text,
    lastreadcontentstatus int,
    progress int,
    status int,
    optional_nodes set<text>,
    PRIMARY KEY (userid, collectionid, contextid)
) WITH CLUSTERING ORDER BY (collectionid ASC, contextid ASC)
    AND transactions = {'enabled': 'true'};

CREATE INDEX IF NOT EXISTS user_enrolments_by_collection ON sunbird_courses_test.user_enrolments (collectionid, userid, contextid)
    INCLUDE (status, completionpercentage, enrolled_date, datetime);

/* --- course_batch: a batch belongs to a collection (root batch or chained rootBatch:courseId child). --- */
CREATE TABLE IF NOT EXISTS sunbird_courses_test.course_batch (
    collectionid text,
    contextid text,
    cert_templates map<text, frozen<map<text, text>>>,
    created_date timestamp,
    createdby text,
    createddate text,
    createdfor list<text>,
    description text,
    end_date timestamp,
    enddate text,
    enrollment_enddate timestamp,
    enrollmentenddate text,
    enrollmenttype text,
    mentors list<text>,
    name text,
    start_date timestamp,
    startdate text,
    status int,
    tandc boolean,
    updated_date timestamp,
    updateddate text,
    PRIMARY KEY (collectionid, contextid)
) WITH CLUSTERING ORDER BY (contextid ASC);

/* --- user_activity_agg: per-node rollup aggregate (activity_id = node do-id, context_id = "cb:"+contextid). --- */
CREATE TABLE IF NOT EXISTS sunbird_courses_test.user_activity_agg (
    activity_type text,
    activity_id text,
    user_id text,
    context_id text,
    agg map<text, int>,
    agg_details list<text>,
    agg_last_updated map<text, timestamp>,
    aggregates map<text, double>,
    PRIMARY KEY ((activity_type, activity_id, user_id), context_id)
) WITH CLUSTERING ORDER BY (context_id ASC);

/* --- assessment_aggregator: per-attempt scores. Identity generalised to collection_id/context_id. --- */
CREATE TABLE IF NOT EXISTS sunbird_courses_test.assessment_aggregator (
    collection_id text,
    context_id text,
    user_id text,
    content_id text,
    attempt_id text,
    created_on timestamp,
    grand_total text,
    last_attempted_on timestamp,
    question list<frozen<question>>,
    total_max_score double,
    total_score double,
    updated_on timestamp,
    PRIMARY KEY (collection_id, context_id, user_id, content_id, attempt_id)
) WITH CLUSTERING ORDER BY (context_id ASC, user_id ASC, content_id ASC, attempt_id ASC)
    AND transactions = {'enabled': 'true'};

-- getUserAssessments filters by user_id (not the partition key) -> needs this index.
CREATE INDEX IF NOT EXISTS assessment_aggregator_by_user ON sunbird_courses_test.assessment_aggregator (user_id, collection_id, context_id, content_id, attempt_id)
    INCLUDE (total_score, total_max_score, last_attempted_on);

/* --- user_skills: durable achieved-skill set, credited once at LP completion (design §6). --- */
CREATE TABLE IF NOT EXISTS sunbird_courses_test.user_skills (
    userid text PRIMARY KEY,
    skills set<text>
);
