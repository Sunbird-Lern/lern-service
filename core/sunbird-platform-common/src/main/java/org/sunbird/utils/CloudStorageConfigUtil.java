package org.sunbird.utils;

import org.sunbird.cloud.storage.StorageConfig;
import org.sunbird.logging.LoggerUtil;

/**
 * Shared helpers for resolving cloud storage configuration strings to SDK enums.
 * Used by both {@link CloudStorageUtil} and {@code modules.StorageModule}.
 */
public final class CloudStorageConfigUtil {

    private static final LoggerUtil logger = new LoggerUtil(CloudStorageConfigUtil.class);

    private CloudStorageConfigUtil() {}

    /**
     * Maps a string storage type (e.g. {@code "azure"}) to {@link StorageConfig.StorageType}.
     */
    public static StorageConfig.StorageType resolveStorageType(String storageType) {
        switch (storageType.toLowerCase()) {
            case "azure":   return StorageConfig.StorageType.AZURE;
            case "aws":     return StorageConfig.StorageType.AWS;
            case "gcloud":  return StorageConfig.StorageType.GCLOUD;
            case "oci":     return StorageConfig.StorageType.OCI;
            case "cephs3":  return StorageConfig.StorageType.CEPHS3;
            default:
                throw new IllegalArgumentException("Unknown cloud storage type: " + storageType);
        }
    }

    /**
     * Maps a string auth type (e.g. {@code "access_key"}, {@code "oidc"}) to
     * {@link StorageConfig.AuthType}. Case-insensitive; hyphens are treated as underscores.
     * Falls back to {@link StorageConfig.AuthType#ACCESS_KEY} for unrecognised values.
     */
    public static StorageConfig.AuthType resolveAuthType(String authType) {
        String normalised = authType.replace("-", "_").toUpperCase();
        try {
            return StorageConfig.AuthType.valueOf(normalised);
        } catch (IllegalArgumentException e) {
            logger.warn("CloudStorageConfigUtil: Unknown auth type '" + authType + "', falling back to ACCESS_KEY");
            return StorageConfig.AuthType.ACCESS_KEY;
        }
    }
}
