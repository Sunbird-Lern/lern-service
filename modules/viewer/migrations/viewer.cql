/*
 * Viewer module — generalise the course tables to COLLECTION tables.
 * Keyspace: sunbird_courses. Authoritative live schema:
 *   sunbird-spark-installer/scripts/sunbird-yugabyte-migrations/sunbird-lern/sunbird_courses.cql
 *
 * DECISION: generalise for collection (not course). Rename the identifying columns to
 * collectionid / contextid, and add per-learner optionality.
 *
 * WHY RENAME (not new table / not backfill):
 *  - courseid / batchid are PRIMARY-KEY columns; Cassandra/Yugabyte RENAME of PK columns is
 *    METADATA-ONLY: instant, no data copy, no backfill, no PK restructure (same key, new names).
 *  - Existing data carries over correctly: old courseid -> collectionid, old batchid -> contextid.
 *    A course IS a collection, so legacy rows are already right (collectionid = the course do-id,
 *    contextid = the batch). No row-level migration.
 *
 * COST (code, not data) — must ship in lockstep:
 *  - Every reader/writer of courseid/batchid switches to collectionid/contextid:
 *    ActivityAggregateUtil (createProgressUpdateMap / createContentConsumptionUpdateMap),
 *    LMS actors, lern-data-pipeline jobs.
 *  - Secondary index on courseid is dropped + recreated on collectionid (below).
 *
 * NOTE: verify RENAME support + index handling on the target Yugabyte (YCQL) version before prod.
 */

/* --- user_content_consumption: generalise identity to collection --- */
ALTER TABLE sunbird_courses.user_content_consumption RENAME courseid TO collectionid;
ALTER TABLE sunbird_courses.user_content_consumption RENAME batchid  TO contextid;

/* --- user_enrolments: generalise identity + add per-learner optionality ---
 * VERIFIED on YCQL (cossdev): a column used in an index CANNOT be renamed
 * ("Feature Not Yet Implemented. Can't rename column used in an index").
 * So DROP the index BEFORE renaming, then recreate it on the new name. Order matters. */
DROP INDEX IF EXISTS sunbird_courses.user_enrolments_by_course;
ALTER TABLE sunbird_courses.user_enrolments RENAME courseid TO collectionid;
ALTER TABLE sunbird_courses.user_enrolments RENAME batchid  TO contextid;
ALTER TABLE sunbird_courses.user_enrolments ADD optional_nodes set<text>;  -- per-enrolment optional child/leaf ids (null = strict)
-- recreate with the SAME shape as the live index (composite + INCLUDE), just on the new column name
CREATE INDEX IF NOT EXISTS user_enrolments_by_collection ON sunbird_courses.user_enrolments (collectionid, userid, contextid)
    INCLUDE (status, completionpercentage, enrolled_date, datetime);  -- index build scans the table; run off-peak on large data

/* --- course_batch: generalise identity (batch belongs to a collection); no index -> rename directly --- */
ALTER TABLE sunbird_courses.course_batch RENAME courseid TO collectionid;
ALTER TABLE sunbird_courses.course_batch RENAME batchid  TO contextid;

/* --- assessment_aggregator: generalise identity (viewer now OWNS the assessment path) ---
 * PK ((course_id, batch_id), user_id, content_id, attempt_id). course_id/batch_id are PARTITION-KEY
 * columns -> RENAME is metadata-only (same as the others). The standard by_user index is on user_id
 * and is unaffected; IF any index references course_id/batch_id, DROP it first then recreate on the
 * new name (see the user_enrolments pattern above). NOTE snake_case names here (matches this table). */
DROP INDEX IF EXISTS sunbird_courses.assessment_aggregator_by_user;  -- only if it references course_id/batch_id; harmless otherwise
ALTER TABLE sunbird_courses.assessment_aggregator RENAME course_id TO collection_id;
ALTER TABLE sunbird_courses.assessment_aggregator RENAME batch_id  TO context_id;
-- recreate the by_user index if you dropped it (adjust columns to your live definition):
-- CREATE INDEX IF NOT EXISTS assessment_aggregator_by_user ON sunbird_courses.assessment_aggregator (user_id);

/*
 * Reference — post-rename live tables the viewer uses (do NOT recreate):
 *
 * course_batch                PK (collectionid, contextid)
 * user_enrolments             PK (userid, collectionid, contextid)                 -- + optional_nodes
 *   used by viewer: progress, status, completionpercentage, contentstatus, completedon, lastread*
 * user_content_consumption    PK (userid, collectionid, contextid, contentid)
 *   used by viewer: status, progress, progressdetails, completedcount, viewcount, last_*_time
 * user_activity_agg           -- unchanged; aggregate identified by collectionId (activity_id = collectionId)
 *   per user + context_id = "cb:"+contextid
 *
 * assessment_aggregator: PK ((collection_id, context_id), user_id, content_id, attempt_id)  -- renamed above
 *   used by viewer: total_score, total_max_score, grand_total, question, created_on, last_attempted_on
 */

/*
 * LP durable skill store (design §6). Per-user achieved-skill set, credited ONCE at LP completion.
 * NOTE: distinct from the legacy `sunbird.user_skills` endorsement table (different keyspace) — no collision.
 */
CREATE TABLE IF NOT EXISTS sunbird_courses.user_skills (
    userid text PRIMARY KEY,
    skills set<text>
);
