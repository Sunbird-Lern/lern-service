package org.sunbird.exception;

/**
 * Unified wrapper for Cassandra-related exceptions.
 * 
 * CM-02: Centralizes exception handling for:
 * - QueryExecutionException, QueryValidationException
 * - NoHostAvailableException, IllegalStateException
 * - WriteTimeoutException
 *
 * Usage:
 *   try {
 *       // cassandra operation
 *   } catch (Exception e) {
 *       throw CassandraException.wrap(e, "batchInsert", keyspace);
 *   }
 */
public class CassandraException extends RuntimeException {

    private static final long serialVersionUID = 1L;
    private final String operation;
    private final String keyspace;

    public CassandraException(String message, Throwable cause, String operation, String keyspace) {
        super(message, cause);
        this.operation = operation;
        this.keyspace = keyspace;
    }

    public String getOperation() {
        return operation;
    }

    public String getKeyspace() {
        return keyspace;
    }

    /**
     * Factory method to wrap any Cassandra-related exception.
     */
    public static CassandraException wrap(Exception e, String operation, String keyspace) {
        String message = String.format("Cassandra %s failed on keyspace '%s': %s",
                operation, keyspace, e.getMessage());
        return new CassandraException(message, e, operation, keyspace);
    }

    /**
     * Factory method when keyspace is unknown.
     */
    public static CassandraException wrap(Exception e, String operation) {
        return wrap(e, operation, "unknown");
    }
}
