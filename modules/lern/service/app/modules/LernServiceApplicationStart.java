package modules;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.concurrent.CompletableFuture;
import org.sunbird.auth.verifier.KeyManager;
import org.sunbird.logging.LoggerUtil;
import org.sunbird.common.ProjectUtil;
import play.api.Environment;
import play.api.inject.ApplicationLifecycle;

@Singleton
public class LernServiceApplicationStart {
    private static LoggerUtil logger = new LoggerUtil(LernServiceApplicationStart.class);
    public static ProjectUtil.Environment env;
    public static String ssoPublicKey = "";

    @Inject
    public LernServiceApplicationStart(ApplicationLifecycle lifecycle, Environment environment) {
        logger.info("======================================================================");
        logger.info("ApplicationStart: Starting Lern Service (UserOrg + LMS + Notification)");
        logger.info("======================================================================");

        setEnvironment(environment);
        ssoPublicKey = System.getenv("sso_public_key"); // JsonKey.SSO_PUBLIC_KEY
        
        logger.info("Environment: " + env.name());
        
        // 1. Initialize Shared Resources (Cassandra, KeyManager, etc.)
        initializeSharedResources();
        
        // 2. Initialize Service-Specific Components
        initializeUserOrgComponents();
        initializeLMSComponents();
        initializeNotificationComponents();

        lifecycle.addStopHook(() -> {
            logger.info("ApplicationStart: Stopping Lern Service");
            return CompletableFuture.completedFuture(null);
        });

        logger.info("ApplicationStart: Lern Service Started Successfully");
        logger.info("======================================================================");
    }

    private void setEnvironment(Environment environment) {
        if (environment.asJava().isDev()) {
            env = ProjectUtil.Environment.dev;
        } else if (environment.asJava().isTest()) {
            env = ProjectUtil.Environment.qa;
        } else {
            env = ProjectUtil.Environment.prod;
        }
    }

    private void initializeSharedResources() {
        logger.info("Initializing Shared Resources...");

        // Initialize Cassandra Connections (Shared) - CRITICAL
        try {
            org.sunbird.helper.CassandraConnectionManager cassandraConnectionManager =
                org.sunbird.helper.CassandraConnectionMngrFactory.getInstance();

            String nodes = System.getenv("sunbird_cassandra_host"); // JsonKey.SUNBIRD_CASSANDRA_IP
            String[] hosts = null;
            if (nodes != null && !nodes.isEmpty()) {
                hosts = nodes.split(",");
            } else {
                hosts = new String[] {"localhost"};
            }
            cassandraConnectionManager.createConnection(hosts);
            logger.info("Cassandra connections established");
        } catch (Exception cassandraException) {
            logger.error("CRITICAL: Failed to establish Cassandra connections. Service startup aborted.", cassandraException);
            throw new RuntimeException("Service startup failed - Cassandra unavailable", cassandraException);
        }

        // Initialize KeyManager - CRITICAL: Service cannot start without public keys
        try {
            KeyManager.init();
            logger.info("KeyManager initialized successfully");
        } catch (Exception keyManagerException) {
            logger.error("CRITICAL: Failed to initialize KeyManager. Service startup aborted.", keyManagerException);
            throw new RuntimeException("Service startup failed - KeyManager initialization failed", keyManagerException);
        }

        // Initialize HTTP Client - Non-critical
        try {
            org.sunbird.http.HttpClientUtil.getInstance();
            logger.info("HTTP Client initialized");
        } catch (Exception httpException) {
            logger.warn("Warning: Failed to initialize HTTP Client. Service may have limited functionality.", httpException);
        }

        // Initialize Kafka Client (Eagerly) - Non-critical
        try {
            org.sunbird.kafka.KafkaClient.init();
            logger.info("Kafka Client initialized");
        } catch (Exception kafkaException) {
            logger.warn("Warning: Failed to initialize Kafka Client. Event publishing may be unavailable.", kafkaException);
        }
    }

    private void initializeUserOrgComponents() {
        logger.info("Initializing UserOrg Components...");
        try {
            org.sunbird.util.user.SchedulerManager.schedule();
            logger.info("UserOrg Scheduler started");
        } catch (Exception e) {
            logger.error("Error starting UserOrg components", e);
        }
    }

    private void initializeLMSComponents() {
        logger.info("Initializing LMS Components...");
        try {
            org.sunbird.learner.util.SchedulerManager.schedule();
            logger.info("LMS Scheduler started");
        } catch (Exception e) {
            logger.error("Error starting LMS components", e);
        }
        if (Boolean.parseBoolean(ProjectUtil.getConfigValue("content_service_mock_enabled"))) {
             try {
                org.sunbird.learner.util.ContentSearchMock.setup();
                logger.info("LMS Content Search Mock setup complete");
             } catch (Exception e) {
                logger.error("Error setting up ContentSearchMock", e);
             }
        }
    }

    private void initializeNotificationComponents() {
        logger.info("Initializing Notification Components...");
        try {
             org.sunbird.Application.getInstance().init();
             logger.info("Notification Application initialized");
        } catch (Exception e) {
            logger.error("Error initializing Notification components", e);
        }
    }
}
