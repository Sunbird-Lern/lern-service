package org.sunbird.helper;

import java.util.List;
import java.util.Map;

/**
 * Value object to encapsulate Cassandra query parameters.
 * Reduces method signatures from 5-8 parameters to 1-2 objects.
 * 
 * CM-04: Simplifies method signatures and improves readability.
 *
 * Usage:
 *   CassandraQueryOptions options = new CassandraQueryOptions.Builder()
 *       .keyspace("my_keyspace")
 *       .table("my_table")
 *       .compositeKey(keyMap)
 *       .projectFields(Arrays.asList("col1", "col2"))
 *       .filters(filterMap)
 *       .build();
 *
 *   response = operation.getRecords(options, requestContext);
 */
public class CassandraQueryOptions {
    private final String keyspaceName;
    private final String tableName;
    private final Map<String, Object> compositeKey;
    private final List<String> projectedFields;
    private final Map<String, Object> filters;

    private CassandraQueryOptions(Builder builder) {
        this.keyspaceName = builder.keyspaceName;
        this.tableName = builder.tableName;
        this.compositeKey = builder.compositeKey;
        this.projectedFields = builder.projectedFields;
        this.filters = builder.filters;
    }

    public String getKeyspaceName() {
        return keyspaceName;
    }

    public String getTableName() {
        return tableName;
    }

    public Map<String, Object> getCompositeKey() {
        return compositeKey;
    }

    public List<String> getProjectedFields() {
        return projectedFields;
    }

    public Map<String, Object> getFilters() {
        return filters;
    }

    /**
     * Builder pattern for easier construction with optional fields.
     */
    public static class Builder {
        private String keyspaceName;
        private String tableName;
        private Map<String, Object> compositeKey;
        private List<String> projectedFields;
        private Map<String, Object> filters;

        public Builder keyspace(String keyspaceName) {
            this.keyspaceName = keyspaceName;
            return this;
        }

        public Builder table(String tableName) {
            this.tableName = tableName;
            return this;
        }

        public Builder compositeKey(Map<String, Object> key) {
            this.compositeKey = key;
            return this;
        }

        public Builder projectFields(List<String> fields) {
            this.projectedFields = fields;
            return this;
        }

        public Builder filters(Map<String, Object> filters) {
            this.filters = filters;
            return this;
        }

        public CassandraQueryOptions build() {
            return new CassandraQueryOptions(this);
        }
    }
}
