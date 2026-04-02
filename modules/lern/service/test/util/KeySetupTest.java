package util;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

/**
 * Utility to setup test public keys for testing KeyManager initialization.
 */
public class KeySetupTest {

    /**
     * Creates a temporary directory with test public keys.
     * @return Path to the test keys directory
     */
    public static String setupTestKeys() throws Exception {
        // Create a temporary directory for test keys
        String testKeysDir = System.getProperty("java.io.tmpdir") + "/sunbird-test-keys-" + UUID.randomUUID();
        Path testKeysDirPath = Paths.get(testKeysDir);
        Files.createDirectories(testKeysDirPath);

        // Create a test public key file
        String testPublicKey = "-----BEGIN PUBLIC KEY-----\n" +
                "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA0Z3VS5JJcds3g8AQFL/y\n" +
                "nRxIeEWn7s64tC/2zcE/Cr0tXdkZELHEf5h7tW6w1v7bWFyI8AuHONzVXkFlx2r9\n" +
                "FXqTDkP4yVLxNpVpKKqOPqLMYe5O8XaKvXjK5qT8m7LqS7LKf8K4VQXf/cT8PQAB\n" +
                "VPEo5U9z9XQ9Wp/s4j4X3Q3VZZ8jV8nF7VqVXQVLXVV8vQVVLpUVVVZ7FVVVVlU8\n" +
                "VVq9SZkQ8QVq9SZkQ8QVq9SZkQ8QVq9SZkQ8QVq9SZkQ8QVq9SZkQ8QVq9SZkQ8Q\n" +
                "VVq9SZkQ8QVq9SZkQ8QVq9SZkQ8QVq9SZkQ8QVq9SZkQ8QVq9SZkQ8QVq9SZkQ8Q\n" +
                "VQIDAQAB\n" +
                "-----END PUBLIC KEY-----";

        // Write test key to file
        Path keyFilePath = testKeysDirPath.resolve("test-key.pem");
        Files.write(keyFilePath, testPublicKey.getBytes());

        return testKeysDir;
    }

    /**
     * Cleans up test keys directory.
     * @param testKeysDir Path to the test keys directory
     */
    public static void cleanupTestKeys(String testKeysDir) throws Exception {
        Path testKeysDirPath = Paths.get(testKeysDir);
        if (Files.exists(testKeysDirPath)) {
            Files.walk(testKeysDirPath)
                    .sorted((p1, p2) -> p2.compareTo(p1)) // Reverse order to delete files before directories
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                        } catch (Exception e) {
                            // Ignore
                        }
                    });
        }
    }

    /**
     * Sets the KeyManager base path to use test keys.
     * @param testKeysDir Path to the test keys directory
     */
    public static void setTestKeyPath(String testKeysDir) {
        System.setProperty("sunbird_access_token_publickey_basepath", testKeysDir);
    }
}
