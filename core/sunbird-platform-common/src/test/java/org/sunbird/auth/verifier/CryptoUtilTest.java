package org.sunbird.auth.verifier;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.util.HashMap;
import java.util.Map;
import org.junit.BeforeClass;
import org.junit.Test;
import org.sunbird.keys.JsonKey;

public class CryptoUtilTest {

    private static PublicKey publicKey;
    private static PrivateKey privateKey;

    @BeforeClass
    public static void setUp() throws NoSuchAlgorithmException {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        KeyPair kp = kpg.generateKeyPair();
        publicKey = kp.getPublic();
        privateKey = kp.getPrivate();
    }

    @Test
    public void testVerifyRSASignSuccess() throws Exception {
        String payload = "test payload";
        byte[] signature = sign(payload, privateKey);

        boolean isValid = CryptoUtil.verifyRSASign(payload, signature, publicKey, JsonKey.SHA_256_WITH_RSA);
        assertTrue(isValid);
    }

    @Test
    public void testVerifyRSASignFailureModifiedPayload() throws Exception {
        String payload = "test payload";
        byte[] signature = sign(payload, privateKey);

        boolean isValid = CryptoUtil.verifyRSASign(payload + "modified", signature, publicKey, JsonKey.SHA_256_WITH_RSA);
        assertFalse(isValid);
    }

    @Test
    public void testVerifyRSASignFailureInvalidSignature() {
        String payload = "test payload";
        byte[] signature = new byte[256]; // Empty signature

        boolean isValid = CryptoUtil.verifyRSASign(payload, signature, publicKey, JsonKey.SHA_256_WITH_RSA);
        assertFalse(isValid);
    }

    @Test
    public void testVerifyRSASignWithContextSuccess() throws Exception {
        String payload = "test payload with context";
        byte[] signature = sign(payload, privateKey);
        Map<String, Object> context = new HashMap<>();
        context.put("requestId", "123");

        boolean isValid = CryptoUtil.verifyRSASign(payload, signature, publicKey, JsonKey.SHA_256_WITH_RSA, context);
        assertTrue(isValid);
    }

    @Test
    public void testVerifyRSASignInvalidAlgorithm() throws Exception {
        String payload = "test payload";
        byte[] signature = sign(payload, privateKey);

        boolean isValid = CryptoUtil.verifyRSASign(payload, signature, publicKey, "InvalidAlgorithm");
        assertFalse(isValid);
    }

    private byte[] sign(String data, PrivateKey key) throws Exception {
        Signature signer = Signature.getInstance(JsonKey.SHA_256_WITH_RSA);
        signer.initSign(key);
        signer.update(data.getBytes(StandardCharsets.US_ASCII));
        return signer.sign();
    }
}