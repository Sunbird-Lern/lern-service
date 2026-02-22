package org.sunbird.auth.verifier;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.security.PublicKey;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import org.keycloak.common.util.Time;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

public class AccessTokenValidatorTest {

    @Test
    public void verifyUserAccessToken() throws JsonProcessingException {
        try (MockedStatic<CryptoUtil> mockedCrypto = Mockito.mockStatic(CryptoUtil.class);
             MockedStatic<Base64Util> mockedBase64 = Mockito.mockStatic(Base64Util.class);
             MockedStatic<KeyManager> mockedKeyManager = Mockito.mockStatic(KeyManager.class)) {

            KeyData keyData = Mockito.mock(KeyData.class);
            mockedKeyManager.when(() -> KeyManager.getPublicKey(anyString())).thenReturn(keyData);
            PublicKey publicKey = Mockito.mock(PublicKey.class);
            Mockito.when(keyData.getPublicKey()).thenReturn(publicKey);
            
            Map<String, Object> payload = new HashMap<>();
            int expTime = Time.currentTime() + 3600;
            payload.put("exp", expTime);
            payload.put("iss", "nullrealms/null");
            payload.put("kid", "kid");
            payload.put("sub", "f:cassandrafederationid:10cca27c-2a13-443c-9e2b-c7d9589c1f5f");
            ObjectMapper mapper = new ObjectMapper();
            
            mockedBase64.when(() -> Base64Util.decode(any(String.class), anyInt()))
                    .thenReturn(mapper.writeValueAsString(payload).getBytes());
            
            mockedCrypto.when(() -> CryptoUtil.verifyRSASign(anyString(), any(), any(), any()))
                    .thenReturn(true);
            
            String userId = AccessTokenValidator.verifyUserToken("header.payload.signature", true);
            assertNotNull(userId);
        }
    }

    @Test
    public void verifyUserAccessTokenInvalidToken() throws JsonProcessingException {
        try (MockedStatic<CryptoUtil> mockedCrypto = Mockito.mockStatic(CryptoUtil.class);
             MockedStatic<Base64Util> mockedBase64 = Mockito.mockStatic(Base64Util.class);
             MockedStatic<KeyManager> mockedKeyManager = Mockito.mockStatic(KeyManager.class)) {

            KeyData keyData = Mockito.mock(KeyData.class);
            mockedKeyManager.when(() -> KeyManager.getPublicKey(anyString())).thenReturn(keyData);
            PublicKey publicKey = Mockito.mock(PublicKey.class);
            Mockito.when(keyData.getPublicKey()).thenReturn(publicKey);
            
            Map<String, Object> payload = new HashMap<>();
            int expTime = Time.currentTime() + 3600;
            payload.put("exp", expTime);
            payload.put("kid", "kid");
            ObjectMapper mapper = new ObjectMapper();
            
            mockedBase64.when(() -> Base64Util.decode(any(String.class), anyInt()))
                    .thenReturn(mapper.writeValueAsString(payload).getBytes());
            
            mockedCrypto.when(() -> CryptoUtil.verifyRSASign(anyString(), any(), any(), any()))
                    .thenReturn(false);
            
            String userId = AccessTokenValidator.verifyUserToken("header.payload.signature", true);
            assertEquals("Unauthorized", userId);
        }
    }

    @Test
    public void verifyUserAccessTokenExpiredToken() throws JsonProcessingException {
        try (MockedStatic<CryptoUtil> mockedCrypto = Mockito.mockStatic(CryptoUtil.class);
             MockedStatic<Base64Util> mockedBase64 = Mockito.mockStatic(Base64Util.class);
             MockedStatic<KeyManager> mockedKeyManager = Mockito.mockStatic(KeyManager.class)) {

            KeyData keyData = Mockito.mock(KeyData.class);
            mockedKeyManager.when(() -> KeyManager.getPublicKey(anyString())).thenReturn(keyData);
            PublicKey publicKey = Mockito.mock(PublicKey.class);
            Mockito.when(keyData.getPublicKey()).thenReturn(publicKey);
            
            Map<String, Object> payload = new HashMap<>();
            int expTime = Time.currentTime() - 3600;
            payload.put("exp", expTime);
            payload.put("kid", "kid");
            ObjectMapper mapper = new ObjectMapper();
            
            mockedBase64.when(() -> Base64Util.decode(any(String.class), anyInt()))
                    .thenReturn(mapper.writeValueAsString(payload).getBytes());
            
            mockedCrypto.when(() -> CryptoUtil.verifyRSASign(anyString(), any(), any(), any()))
                    .thenReturn(true);
            
            String userId = AccessTokenValidator.verifyUserToken("header.payload.signature", true);
            assertEquals("Unauthorized", userId);
        }
    }

    @Test
    public void verifyToken() throws JsonProcessingException {
        try (MockedStatic<CryptoUtil> mockedCrypto = Mockito.mockStatic(CryptoUtil.class);
             MockedStatic<Base64Util> mockedBase64 = Mockito.mockStatic(Base64Util.class);
             MockedStatic<KeyManager> mockedKeyManager = Mockito.mockStatic(KeyManager.class)) {

            KeyData keyData = Mockito.mock(KeyData.class);
            mockedKeyManager.when(() -> KeyManager.getPublicKey(anyString())).thenReturn(keyData);
            PublicKey publicKey = Mockito.mock(PublicKey.class);
            Mockito.when(keyData.getPublicKey()).thenReturn(publicKey);
            
            Map<String, Object> payload = new HashMap<>();
            int expTime = Time.currentTime() + 3600;
            payload.put("exp", expTime);
            payload.put("requestedByUserId", "386c7960-7f85-4a24-8131-a8aba519ce7d");
            payload.put("requestedForUserId", "386c7960-7f85-4a24-8131-a8aba519ce7e");
            payload.put("kid", "kid");
            payload.put("parentId", "386c7960-7f85-4a24-8131-a8aba519ce7d");
            payload.put("sub", "386c7960-7f85-4a24-8131-a8aba519ce7e");
            ObjectMapper mapper = new ObjectMapper();
            
            mockedBase64.when(() -> Base64Util.decode(any(String.class), anyInt()))
                    .thenReturn(mapper.writeValueAsString(payload).getBytes());
            
            mockedCrypto.when(() -> CryptoUtil.verifyRSASign(anyString(), any(), any(), any()))
                    .thenReturn(true);
            
            String userId = AccessTokenValidator.verifyManagedUserToken(
                    "header.payload.signature",
                    "386c7960-7f85-4a24-8131-a8aba519ce7d", "386c7960-7f85-4a24-8131-a8aba519ce7d", "");
            assertNotNull(userId);
        }
    }

    @Test
    public void verifyTokenWithNullParentId() throws JsonProcessingException {
        try (MockedStatic<CryptoUtil> mockedCrypto = Mockito.mockStatic(CryptoUtil.class);
             MockedStatic<Base64Util> mockedBase64 = Mockito.mockStatic(Base64Util.class);
             MockedStatic<KeyManager> mockedKeyManager = Mockito.mockStatic(KeyManager.class)) {

            KeyData keyData = Mockito.mock(KeyData.class);
            mockedKeyManager.when(() -> KeyManager.getPublicKey(anyString())).thenReturn(keyData);
            PublicKey publicKey = Mockito.mock(PublicKey.class);
            Mockito.when(keyData.getPublicKey()).thenReturn(publicKey);
            
            Map<String, Object> payload = new HashMap<>();
            int expTime = Time.currentTime() + 3600;
            payload.put("exp", expTime);
            payload.put("requestedByUserId", "386c7960-7f85-4a24-8131-a8aba519ce7d");
            payload.put("requestedForUserId", "386c7960-7f85-4a24-8131-a8aba519ce7e");
            payload.put("kid", "kid");
            payload.put("sub", "386c7960-7f85-4a24-8131-a8aba519ce7e");
            ObjectMapper mapper = new ObjectMapper();
            
            mockedBase64.when(() -> Base64Util.decode(any(String.class), anyInt()))
                    .thenReturn(mapper.writeValueAsString(payload).getBytes());
            
            mockedCrypto.when(() -> CryptoUtil.verifyRSASign(anyString(), any(), any(), any()))
                    .thenReturn(true);
            
            String userId = AccessTokenValidator.verifyManagedUserToken(
                    "header.payload.signature",
                    "386c7960-7f85-4a24-8131-a8aba519ce7d", "386c7960-7f85-4a24-8131-a8aba519ce7d", "");
            assertEquals("Unauthorized", userId);
        }
    }

    @Test
    public void verifySourceUserToken() throws JsonProcessingException {
        try (MockedStatic<CryptoUtil> mockedCrypto = Mockito.mockStatic(CryptoUtil.class);
             MockedStatic<Base64Util> mockedBase64 = Mockito.mockStatic(Base64Util.class);
             MockedStatic<KeyManager> mockedKeyManager = Mockito.mockStatic(KeyManager.class)) {

            KeyData keyData = Mockito.mock(KeyData.class);
            mockedKeyManager.when(() -> KeyManager.getPublicKey(anyString())).thenReturn(keyData);
            PublicKey publicKey = Mockito.mock(PublicKey.class);
            Mockito.when(keyData.getPublicKey()).thenReturn(publicKey);
            
            Map<String, Object> payload = new HashMap<>();
            int expTime = Time.currentTime() + 3600;
            payload.put("exp", expTime);
            payload.put("iss", "http://localhost:8080/auth/realms/master");
            payload.put("kid", "kid");
            payload.put("sub", "f:cassandrafederationid:10cca27c-2a13-443c-9e2b-c7d9589c1f5f");
            ObjectMapper mapper = new ObjectMapper();
            
            mockedBase64.when(() -> Base64Util.decode(any(String.class), anyInt()))
                    .thenReturn(mapper.writeValueAsString(payload).getBytes());
            
            mockedCrypto.when(() -> CryptoUtil.verifyRSASign(anyString(), any(), any(), any()))
                    .thenReturn(true);
            
            String userId = AccessTokenValidator.verifySourceUserToken(
                    "header.payload.signature",
                    "http://localhost:8080/auth/",
                    new HashMap<>());
            assertNotNull(userId);
        }
    }

    @Test
    public void verifyUserAccessTokenInvalidFormat() {
        String userId = AccessTokenValidator.verifyUserToken("invalid.token", true);
        assertEquals("Unauthorized", userId);
    }
}