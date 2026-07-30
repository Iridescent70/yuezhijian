package cn.iocoder.yudao.module.member.framework.security;

import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.StrUtil;
import cn.hutool.extra.spring.SpringUtil;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * 会员手机号保护工具。
 *
 * <p>手机号正文由 {@link MemberMobileEncryptTypeHandler} 加密；该类只产生带独立 pepper 的
 * 检索哈希和尾号，避免为了登录、查重或列表筛选而保存明文。</p>
 */
public final class MemberMobileProtectionUtils {

    private static final String HASH_PEPPER_PROPERTY = "yuezhijian.data-protection.hash-pepper";

    private MemberMobileProtectionUtils() {
    }

    public static String normalize(String mobile) {
        if (mobile == null) {
            return null;
        }
        String normalized = mobile.replaceAll("[\\s-]", "");
        Assert.isTrue(normalized.matches("1[3-9]\\d{9}"), "请输入正确的11位手机号");
        return normalized;
    }

    public static String searchableHash(String mobile) {
        String normalized = normalize(mobile);
        if (normalized == null) {
            return null;
        }
        String pepper = SpringUtil.getProperty(HASH_PEPPER_PROPERTY);
        Assert.isTrue(StrUtil.length(pepper) >= 16 && !StrUtil.startWith(pepper, "Replace-With"),
                "配置项({}) 必须是至少16位的非占位值", HASH_PEPPER_PROPERTY);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] value = (pepper + ':' + normalized).getBytes(StandardCharsets.UTF_8);
            return HexFormat.of().formatHex(digest.digest(value));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("运行环境不支持 SHA-256", exception);
        }
    }

    public static String searchableHashIfPresent(String mobile) {
        return StrUtil.isBlank(mobile) ? null : searchableHash(mobile);
    }

    public static String last4(String mobile) {
        String normalized = normalize(mobile);
        return normalized == null ? null : StrUtil.subSuf(normalized, normalized.length() - 4);
    }

}
