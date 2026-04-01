package org.sunbird.auth.verifier;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.sunbird.keys.JsonKey;
import org.sunbird.logging.LoggerUtil;
import org.sunbird.common.PropertiesCache;

/**
 * Manages the loading and retrieval of Public Keys for token verification.
 */
public class KeyManager {

    private static final LoggerUtil logger = new LoggerUtil(KeyManager.class);
    private static final PropertiesCache propertiesCache = PropertiesCache.getInstance();
    private static final Map<String, KeyData> keyMap = new HashMap<>();
    private static volatile long lastReloadAttemptMs = 0L;
    private static final long RELOAD_COOLDOWN_MS = 5 * 60 * 1000L; // 5 minutes

    /**
     * Initializes the KeyManager by loading public keys from the configured base path.
     */
    public static void init() {
        String basePath = propertiesCache.getProperty(JsonKey.ACCESS_TOKEN_PUBLICKEY_BASEPATH);
        logger.info("KeyManager:init: Starting public key loading from base path: " + basePath);

        try (Stream<Path> walk = Files.walk(Paths.get(basePath))) {
            List<String> result =
                    walk.filter(Files::isRegularFile).map(x -> x.toString()).collect(Collectors.toList());

            if (result.isEmpty()) {
                String errorMsg = "KeyManager:init: No public key files found in base path: " + basePath + ". Service cannot start without public keys.";
                logger.error(errorMsg);
                throw new RuntimeException(errorMsg);
            }

            result.forEach(
                    file -> {
                        try {
                            StringBuilder contentBuilder = new StringBuilder();
                            Path path = Paths.get(file);
                            Files.lines(path, StandardCharsets.UTF_8)
                                    .forEach(contentBuilder::append);

                            KeyData keyData =
                                    new KeyData(
                                            path.getFileName().toString(), loadPublicKey(contentBuilder.toString()));
                            keyMap.put(path.getFileName().toString(), keyData);
                            logger.info("KeyManager:init: Loaded key: " + path.getFileName().toString());
                        } catch (Exception e) {
                            logger.error("KeyManager:init: Exception in reading public key file: " + file, e);
                        }
                    });

            // CRITICAL: Ensure at least one key was successfully loaded
            // If files exist but are empty/invalid, keyMap will be empty
            if (keyMap.isEmpty()) {
                String errorMsg = "KeyManager:init: Key files exist in " + basePath + " but none could be loaded. " +
                        "Files may be empty, corrupted, or invalid. Found " + result.size() + " file(s) but 0 valid keys.";
                logger.error(errorMsg);
                throw new RuntimeException(errorMsg);
            }

            logger.info("KeyManager:init: Successfully loaded " + keyMap.size() + " public keys");
        } catch (Exception e) {
            String errorMsg = "KeyManager:init: Exception in loading public keys base directory: " + e.getMessage();
            logger.error(errorMsg, e);
            throw new RuntimeException(errorMsg, e);
        }
    }

    /**
     * Checks if keys have been successfully loaded.
     * @return true if at least one key is loaded, false otherwise.
     */
    public static boolean isKeysLoaded() {
        return !keyMap.isEmpty();
    }

    /**
     * Retrieves the KeyData for a given Key ID.
     * If not found in cache, attempts a one-time reload from disk (rate-limited to once per 5 minutes)
     * to self-heal after Keycloak key rotation or a ConfigMap update without requiring a pod restart.
     * @param keyId The Key ID.
     * @return The KeyData object, or null if not found even after reload.
     */
    public static KeyData getPublicKey(String keyId) {
        KeyData keyData = keyMap.get(keyId);
        if (keyData == null) {
            long now = System.currentTimeMillis();
            if (now - lastReloadAttemptMs > RELOAD_COOLDOWN_MS) {
                lastReloadAttemptMs = now;
                logger.info("KeyManager:getPublicKey: Key not found for kid: " + keyId + ", reloading keys from disk");
                try {
                    init();
                    keyData = keyMap.get(keyId);
                } catch (Exception e) {
                    logger.error("KeyManager:getPublicKey: Failed to reload keys from disk for kid: " + keyId, e);
                    // Continue gracefully - let AccessTokenValidator handle null keyData
                }
            } else {
                logger.warn("KeyManager:getPublicKey: Key not found for kid: " + keyId + ", reload skipped (cooldown active)");
            }
        }
        return keyData;
    }

    /**
     * Parses a string representation of a public key into a PublicKey object.
     * @param key The public key string (PEM format).
     * @return The PublicKey object.
     * @throws Exception If parsing fails.
     */
    public static PublicKey loadPublicKey(String key) throws Exception {
        String publicKey = new String(key.getBytes(), StandardCharsets.UTF_8);
        publicKey = publicKey.replaceAll("(-+BEGIN PUBLIC KEY-+)", "");
        publicKey = publicKey.replaceAll("(-+END PUBLIC KEY-+)", "");
        publicKey = publicKey.replaceAll("[\\r\\n]+", "");
        byte[] keyBytes = Base64Util.decode(publicKey.getBytes(StandardCharsets.UTF_8), Base64Util.DEFAULT);

        X509EncodedKeySpec X509publicKey = new X509EncodedKeySpec(keyBytes);
        KeyFactory kf = KeyFactory.getInstance("RSA");
        return kf.generatePublic(X509publicKey);
    }
}
