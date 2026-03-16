package org.sunbird.db;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.sunbird.common.ProjectUtil;
import org.sunbird.logging.LoggerUtil;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Singleton HikariCP connection pool for PostgreSQL / YugabyteDB SQL.
 *
 * <p>Config keys (read via ProjectUtil.getConfigValue):
 * <ul>
 *   <li>sunbird_postgres_host     - DB host (required)</li>
 *   <li>sunbird_postgres_port     - DB port (default: 5432)</li>
 *   <li>sunbird_postgres_db       - Database name (required)</li>
 *   <li>sunbird_postgres_username - Username (required)</li>
 *   <li>sunbird_postgres_password - Password (required)</li>
 *   <li>sunbird_postgres_pool_size - Max pool size (default: 10)</li>
 * </ul>
 *
 * <p>Usage:
 * <pre>
 *   try (Connection conn = PostgreSQLConnectionManager.getInstance().getConnection()) {
 *       // use connection
 *   }
 * </pre>
 */
public class PostgreSQLConnectionManager {

    private static final LoggerUtil logger = new LoggerUtil(PostgreSQLConnectionManager.class);
    private static volatile PostgreSQLConnectionManager instance;
    private final HikariDataSource dataSource;

    private PostgreSQLConnectionManager() {
        String host = ProjectUtil.getConfigValue("sunbird_postgres_host");
        String port = ProjectUtil.getConfigValue("sunbird_postgres_port");
        String db = ProjectUtil.getConfigValue("sunbird_postgres_db");
        String user = ProjectUtil.getConfigValue("sunbird_postgres_username");
        String password = ProjectUtil.getConfigValue("sunbird_postgres_password");
        String poolSizeStr = ProjectUtil.getConfigValue("sunbird_postgres_pool_size");

        // Validate required config values early — fail fast with a clear message
        List<String> missing = new ArrayList<>();
        if (host     == null || host.isEmpty())     missing.add("sunbird_postgres_host");
        if (db       == null || db.isEmpty())       missing.add("sunbird_postgres_db");
        if (user     == null || user.isEmpty())     missing.add("sunbird_postgres_username");
        if (password == null || password.isEmpty()) missing.add("sunbird_postgres_password");
        if (!missing.isEmpty()) {
            throw new IllegalStateException(
                "PostgreSQLConnectionManager: missing required config key(s): " + String.join(", ", missing)
            );
        }

        if (port == null || port.isEmpty()) port = "5432";

        int poolSize = 10;
        if (poolSizeStr != null && !poolSizeStr.isEmpty()) {
            try {
                poolSize = Integer.parseInt(poolSizeStr);
            } catch (NumberFormatException e) {
                logger.warn("PostgreSQLConnectionManager: invalid pool size value '" + poolSizeStr
                        + "' — defaulting to 10. Set sunbird_postgres_pool_size to a valid integer.");
            }
        }

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:postgresql://" + host + ":" + port + "/" + db);
        config.setUsername(user);
        config.setPassword(password);
        config.setDriverClassName("org.postgresql.Driver");
        config.setMaximumPoolSize(poolSize);
        config.setMinimumIdle(2);
        config.setConnectionTimeout(30000);
        config.setIdleTimeout(600000);
        config.setMaxLifetime(1800000);
        config.setConnectionTestQuery("SELECT 1");
        config.setPoolName("sunbird-postgres-pool");

        this.dataSource = new HikariDataSource(config);
        logger.info("PostgreSQLConnectionManager: Connection pool initialized - host=" + host
                + ", port=" + port + ", db=" + db + ", poolSize=" + poolSize);
    }

    public static PostgreSQLConnectionManager getInstance() {
        if (instance == null) {
            synchronized (PostgreSQLConnectionManager.class) {
                if (instance == null) {
                    instance = new PostgreSQLConnectionManager();
                }
            }
        }
        return instance;
    }

    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    /** For testing only — resets singleton so a new one can be created with mock config. */
    static void resetInstance() {
        synchronized (PostgreSQLConnectionManager.class) {
            if (instance != null && instance.dataSource != null) {
                instance.dataSource.close();
            }
            instance = null;
        }
    }
}
