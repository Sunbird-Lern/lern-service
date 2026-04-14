package org.sunbird.auth.verifier;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import org.junit.Test;

public class KeyDataTest {

    @Test
    public void testKeyData() throws NoSuchAlgorithmException {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        KeyPair kp = kpg.generateKeyPair();
        PublicKey publicKey = kp.getPublic();

        KeyData keyData = new KeyData("test-id", publicKey);

        assertNotNull(keyData);
        assertEquals("test-id", keyData.getKeyId());
        assertEquals(publicKey, keyData.getPublicKey());

        keyData.setKeyId("new-id");
        assertEquals("new-id", keyData.getKeyId());

        KeyPair kp2 = kpg.generateKeyPair();
        PublicKey publicKey2 = kp2.getPublic();
        keyData.setPublicKey(publicKey2);
        assertEquals(publicKey2, keyData.getPublicKey());
    }
}