-- One-time setup for the user creation count report.
-- Run this against the YugabyteDB YSQL (PostgreSQL-compatible) instance.
--
-- Prerequisites (run before this script):
--   1. ALTER TABLE sunbird.user ADD createdat date;                         -- Cassandra
--   2. PUT /user/_mapping { "properties": { "createdAt": { "type": "date", "format": "yyyy-MM-dd" } } }
--   3. POST /_reindex (backfill existing users — see docs/observability-elasticsearch-approach.md)

INSERT INTO standard_reports_meta
  (report_id, title, description, domain, data_source, query_template, supported_filters, enabled, aggregation_spec)
VALUES (
  'user-creation-count',
  'User Creation Count by Date Range',
  'Count of users created in a date range. Dates as yyyy-MM-dd (toDate exclusive). If fromDate/toDate are omitted, returns per-month count for the last 12 months.',
  'user_profile',
  'ELASTICSEARCH',
  '{"fromDate":"{{fromDate}}","toDate":"{{toDate}}"}',
  '["fromDate", "toDate"]',
  't',
  NULL
);
