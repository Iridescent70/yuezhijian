package com.yuezhijian.server.common;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.data-protection")
public record DataProtectionProperties(String encryptionKey, String hashPepper) {
}
