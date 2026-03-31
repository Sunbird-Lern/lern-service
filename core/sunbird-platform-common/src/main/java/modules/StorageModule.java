package modules;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import org.apache.commons.lang3.StringUtils;
import org.sunbird.cloud.storage.IStorageService;
import org.sunbird.cloud.storage.StorageConfig;
import org.sunbird.cloud.storage.StorageServiceFactory;
import org.sunbird.common.ProjectUtil;
import org.sunbird.keys.JsonKey;
import org.sunbird.logging.LoggerUtil;

import javax.inject.Singleton;

/**
 * Play/Guice module that provides a singleton {@link IStorageService} instance.
 *
 * <p>Register in application.conf:
 * <pre>
 * play.modules {
 *   enabled += modules.StorageModule
 * }
 * </pre>
 *
 * <p>Config is resolved via {@link ProjectUtil#getConfigValue(String)}, which checks
 * {@code System.getenv(key)} first (K8s ConfigMap / Docker env vars), then falls back
 * to {@code externalresource.properties}. This is the same resolution order used by
 * {@link org.sunbird.utils.CloudStorageUtil} and the rest of the platform.
 *
 * <p>Relevant keys:
 * <pre>
 * sunbird_cloud_service_provider    — azure | aws | gcloud | oci | cephs3  (default: azure)
 * sunbird_cloud_storage_auth_type   — ACCESS_KEY | OIDC | IAM | IAM_ROLE | INSTANCE_PROFILE (default: ACCESS_KEY)
 * sunbird_account_name              — storage account name / AWS access key ID
 * sunbird_account_key               — storage account secret  (ACCESS_KEY auth only)
 * </pre>
 *
 * <p>For OIDC (Kubernetes Workload Identity), only {@code sunbird_account_name} is needed;
 * the cloud SDK resolves credentials automatically from the pod's projected token.
 * Do NOT set {@code sunbird_account_key} in that case.
 */
public class StorageModule extends AbstractModule {

    private static final LoggerUtil logger = new LoggerUtil(StorageModule.class);

    @Override
    protected void configure() {
        // All bindings via @Provides below
    }

    @Provides
    @Singleton
    public IStorageService provideStorageService() {
        String storageTypeStr = defaultIfBlank(ProjectUtil.getConfigValue(JsonKey.CLOUD_SERVICE_PROVIDER), "azure");
        String authTypeStr    = defaultIfBlank(ProjectUtil.getConfigValue(JsonKey.AUTH_TYPE), "ACCESS_KEY");
        String storageKey = ProjectUtil.getConfigValue(JsonKey.ACCOUNT_NAME);

        // Resolve storage type from config value (case-insensitive enum name)
        StorageConfig.StorageType storageType;
        try {
            storageType = StorageConfig.StorageType.valueOf(storageTypeStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid storage type: " + storageTypeStr + ". Must be a valid StorageConfig.StorageType enum value.", e);
        }

        // Resolve auth type from config value (case-insensitive enum name)
        StorageConfig.AuthType authType;
        try {
            authType = StorageConfig.AuthType.valueOf(authTypeStr.replace("-", "_").toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid auth type: " + authTypeStr + ". Must be a valid StorageConfig.AuthType enum value.", e);
        }

        logger.info(
            "StorageModule:provideStorageService: Initialising IStorageService "
            + "storageType=" + storageType + " authType=" + authType);

        StorageConfig.Builder builder = StorageConfig.builder(storageType)
                .storageKey(StringUtils.defaultString(storageKey))
                .authType(authType);

        // For ACCESS_KEY auth, also provide the secret credential.
        // For OIDC / IAM / IAM_ROLE / INSTANCE_PROFILE, the cloud SDK resolves
        // credentials automatically (Workload Identity env vars, instance metadata, etc.).
        if (authType == StorageConfig.AuthType.ACCESS_KEY) {
            String storageSecret = ProjectUtil.getConfigValue(JsonKey.ACCOUNT_KEY);
            builder.storageSecret(StringUtils.defaultString(storageSecret));
        }

        return StorageServiceFactory.getStorageService(builder.build());
    }

    private static String defaultIfBlank(String value, String defaultValue) {
        return StringUtils.isNotBlank(value) ? value : defaultValue;
    }
}
