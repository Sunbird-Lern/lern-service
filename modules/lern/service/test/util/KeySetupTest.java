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

        // Create a test public key file (2048-bit RSA public key for testing)
        String testPublicKey = "-----BEGIN PUBLIC KEY-----\n" +
                "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA4f5wg5l2hKsTeNem/V41\n" +
                "fGnJm6gOdrj8ym3rFkEU/wT8RDtn1tDuWnYLe3bBt7PlJGEe6l0ZCT7bW0oRyUdQ\n" +
                "h4XPBM0z/4LHrZyFYMJiIRWVKfLEFJcW8xVQyPLzKEiSWiBJPBcx2DWLhZEJqq3d\n" +
                "1sKlzP7LcN+5eqP7bZ8/wIgkMKdDwN9VT7XpvHDL6BZQUZg5m3KEr3vf6WU3sI1E\n" +
                "bkpHLPU4EKmNmQv7wf3z01TgkpQ6eqbVf0TJBJxM7oJqDKTW2pqNdkCWMLLN9CMm\n" +
                "Z6hPB6hjNIDwqnmXVCJvh6FHJY/9dXkYxfLFPqHjX3eo0a+Lq1EqL6wJyNcxqNWn\n" +
                "bQIDAQAB\n" +
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
