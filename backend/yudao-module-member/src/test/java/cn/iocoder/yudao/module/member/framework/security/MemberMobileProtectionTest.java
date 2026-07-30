package cn.iocoder.yudao.module.member.framework.security;

import cn.iocoder.yudao.framework.test.core.ut.BaseDbUnitTest;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MemberMobileProtectionTest extends BaseDbUnitTest {

    @Test
    void shouldEncryptWithRandomNonceAndDecrypt() throws SQLException {
        String first = MemberMobileEncryptTypeHandler.encrypt("13800138000");
        String second = MemberMobileEncryptTypeHandler.encrypt("13800138000");

        assertNotEquals(first, second);
        assertEquals("13800138000", MemberMobileEncryptTypeHandler.decrypt(first));
        assertEquals("13800138000", MemberMobileEncryptTypeHandler.decrypt(second));
    }

    @Test
    void shouldRejectTamperedCiphertext() throws SQLException {
        String encrypted = MemberMobileEncryptTypeHandler.encrypt("13800138000");
        String tampered = encrypted.substring(0, encrypted.length() - 1)
                + (encrypted.endsWith("A") ? "B" : "A");

        assertThrows(SQLException.class, () -> MemberMobileEncryptTypeHandler.decrypt(tampered));
    }

    @Test
    void shouldNormalizeAndHashForSearch() {
        assertEquals("13800138000", MemberMobileProtectionUtils.normalize("138 0013-8000"));
        assertEquals(MemberMobileProtectionUtils.searchableHash("13800138000"),
                MemberMobileProtectionUtils.searchableHash("138 0013-8000"));
        assertEquals("8000", MemberMobileProtectionUtils.last4("13800138000"));
    }

}
