package org.sunbird.auth.verifier;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.anyString;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.security.PublicKey;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.powermock.reflect.Whitebox;
import org.sunbird.common.PropertiesCache;
import org.sunbird.keys.JsonKey;

public class KeyManagerTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void testLoadPublicKey() throws Exception {
        PublicKey key =
                KeyManager.loadPublicKey(
                        "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAysH/wWtg0IjBL1JZZDYvUJC42JCxVobalckr2/3d3eEiWkk7Zh/4DAPYOs4UPjAevTs5VMUjq9EZu/u4H5hNzoVmYNvhtxbhWNY3n4mxpA4Lgt4sNGiGYNNGrN34ML+7+TR3Z1dlrhA271PiuanHI11YymskQRPhBfuwK923Kl/lgI4rS9OQ4GnkvwkUPvMUIRfNt8wL9uTbWm3V9p8VTcmQbW+pPw9QhO9v95NOgXQrLnT8xwnzQE6UCTY2al3B0fc3ULmcxvK+7P1R3/0w1qJLEKSiHl0xnv4WNEfS+2UmN+8jfdSCfoyVIglQl5/tb05j89nfZZp8k24AWLxIJQIDAQAB");
        assertNotNull(key);
    }

    @Test
    public void testGetPublicKey() {
        KeyData key = KeyManager.getPublicKey("keyId");
        assertNull(key);
    }

    @Test
    public void testInit() throws IOException {
        File keyFile = folder.newFile("keyId");
        try (FileWriter writer = new FileWriter(keyFile)) {
            writer.write("MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAysH/wWtg0IjBL1JZZDYvUJC42JCxVobalckr2/3d3eEiWkk7Zh/4DAPYOs4UPjAevTs5VMUjq9EZu/u4H5hNzoVmYNvhtxbhWNY3n4mxpA4Lgt4sNGiGYNNGrN34ML+7+TR3Z1dlrhA271PiuanHI11YymskQRPhBfuwK923Kl/lgI4rS9OQ4GnkvwkUPvMUIRfNt8wL9uTbWm3V9p8VTcmQbW+pPw9QhO9v95NOgXQrLnT8xwnzQE6UCTY2al3B0fc3ULmcxvK+7P1R3/0w1qJLEKSiHl0xnv4WNEfS+2UmN+8jfdSCfoyVIglQl5/tb05j89nfZZp8k24AWLxIJQIDAQAB");
        }

        PropertiesCache propertiesCacheMock = Mockito.mock(PropertiesCache.class);
        try (MockedStatic<PropertiesCache> mockedPropertiesCache = Mockito.mockStatic(PropertiesCache.class)) {
            mockedPropertiesCache.when(PropertiesCache::getInstance).thenReturn(propertiesCacheMock);
            Mockito.when(propertiesCacheMock.getProperty(JsonKey.ACCESS_TOKEN_PUBLICKEY_BASEPATH)).thenReturn(folder.getRoot().getAbsolutePath());
            Whitebox.setInternalState(KeyManager.class, "propertiesCache", propertiesCacheMock);

            KeyManager.init();

            KeyData keyData = KeyManager.getPublicKey("keyId");
            assertNotNull(keyData);
            assertNotNull(keyData.getPublicKey());
        }
    }
}