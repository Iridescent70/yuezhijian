package cn.iocoder.yudao.module.member.framework.security;

import cn.hutool.core.lang.Assert;
import cn.hutool.extra.spring.SpringUtil;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Base64;

/**
 * 会员手机号随机加密 TypeHandler。
 *
 * <p>每次写入都使用独立的 96-bit nonce 和 AES-GCM 认证加密；手机号查询走
 * {@code mobile_hash}，因此不依赖可预测密文。</p>
 */
public class MemberMobileEncryptTypeHandler extends BaseTypeHandler<String> {

    private static final String KEY_PROPERTY = "mybatis-plus.encryptor.password";
    private static final String PREFIX = "v1:";
    private static final int NONCE_LENGTH = 12;
    private static final int TAG_LENGTH_BITS = 128;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, String parameter, JdbcType jdbcType)
            throws SQLException {
        ps.setString(i, encrypt(parameter));
    }

    @Override
    public String getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return decrypt(rs.getString(columnName));
    }

    @Override
    public String getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return decrypt(rs.getString(columnIndex));
    }

    @Override
    public String getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return decrypt(cs.getString(columnIndex));
    }

    static String encrypt(String plainText) throws SQLException {
        if (plainText == null) {
            return null;
        }
        try {
            byte[] nonce = new byte[NONCE_LENGTH];
            SECURE_RANDOM.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, getKey(), new GCMParameterSpec(TAG_LENGTH_BITS, nonce));
            byte[] ciphertext = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            byte[] payload = ByteBuffer.allocate(nonce.length + ciphertext.length)
                    .put(nonce).put(ciphertext).array();
            return PREFIX + Base64.getEncoder().encodeToString(payload);
        } catch (GeneralSecurityException exception) {
            throw new SQLException("会员手机号加密失败", exception);
        }
    }

    static String decrypt(String encrypted) throws SQLException {
        if (encrypted == null) {
            return null;
        }
        if (!encrypted.startsWith(PREFIX)) {
            throw new SQLException("会员手机号密文版本不受支持");
        }
        try {
            byte[] payload = Base64.getDecoder().decode(encrypted.substring(PREFIX.length()));
            Assert.isTrue(payload.length > NONCE_LENGTH, "会员手机号密文格式不正确");
            byte[] nonce = new byte[NONCE_LENGTH];
            byte[] ciphertext = new byte[payload.length - NONCE_LENGTH];
            System.arraycopy(payload, 0, nonce, 0, nonce.length);
            System.arraycopy(payload, nonce.length, ciphertext, 0, ciphertext.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, getKey(), new GCMParameterSpec(TAG_LENGTH_BITS, nonce));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException exception) {
            throw new SQLException("会员手机号密文校验失败", exception);
        }
    }

    private static SecretKeySpec getKey() {
        String rawKey = SpringUtil.getProperty(KEY_PROPERTY);
        byte[] key = rawKey == null ? new byte[0] : rawKey.getBytes(StandardCharsets.UTF_8);
        Assert.isTrue(key.length == 16 || key.length == 24 || key.length == 32,
                "配置项({}) 必须是16、24或32字节", KEY_PROPERTY);
        return new SecretKeySpec(key, "AES");
    }

}
