package com.yuezhijian.server.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Base64;
import org.junit.jupiter.api.Test;

class SensitiveDataCodecTest {
    @Test
    void encryptionUsesRandomNonceAndSearchHashIsStable() {
        String key = Base64.getEncoder().encodeToString(new byte[32]);
        SensitiveDataCodec codec = new SensitiveDataCodec(
                new DataProtectionProperties(key, "unit-test-private-pepper"));

        String encryptedFirst = codec.encrypt("13800001001");
        String encryptedSecond = codec.encrypt("13800001001");

        assertThat(encryptedFirst).isNotEqualTo(encryptedSecond);
        assertThat(encryptedFirst).doesNotContain("13800001001");
        assertThat(codec.searchableHash("13800001001"))
                .hasSize(64)
                .isEqualTo(codec.searchableHash("13800001001"));
    }
}
