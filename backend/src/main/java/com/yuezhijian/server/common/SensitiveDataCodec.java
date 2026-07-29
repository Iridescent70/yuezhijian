package com.yuezhijian.server.common;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("sqlserver")
public class SensitiveDataCodec {
    private static final int NONCE_LENGTH = 12;
    private static final int TAG_LENGTH_BITS = 128;
    private static final byte FORMAT_VERSION = 1;

    private final SecretKeySpec key;
    private final String hashPepper;
    private final SecureRandom secureRandom = new SecureRandom();

    public SensitiveDataCodec(DataProtectionProperties properties) {
        byte[] keyBytes;
        try {
            keyBytes = Base64.getDecoder().decode(properties.encryptionKey());
        } catch (RuntimeException exception) {
            throw new IllegalStateException("APP_DATA_ENCRYPTION_KEY 必须是Base64编码的32字节密钥", exception);
        }
        if (keyBytes.length != 32) {
            throw new IllegalStateException("APP_DATA_ENCRYPTION_KEY 解码后必须为32字节");
        }
        if (properties.hashPepper() == null || properties.hashPepper().length() < 16
                || properties.hashPepper().startsWith("Replace-With")) {
            throw new IllegalStateException("APP_DATA_HASH_PEPPER 必须设置为至少16位的非占位值");
        }
        this.key = new SecretKeySpec(keyBytes, "AES");
        this.hashPepper = properties.hashPepper();
    }

    public String encrypt(String plaintext) {
        try {
            byte[] nonce = new byte[NONCE_LENGTH];
            secureRandom.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, nonce));
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            ByteBuffer payload = ByteBuffer.allocate(1 + nonce.length + encrypted.length);
            payload.put(FORMAT_VERSION).put(nonce).put(encrypted);
            return Base64.getEncoder().encodeToString(payload.array());
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("敏感字段加密失败", exception);
        }
    }

    public String searchableHash(String normalizedValue) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest((hashPepper + ':' + normalizedValue).getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(bytes);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("敏感字段检索哈希失败", exception);
        }
    }
}
