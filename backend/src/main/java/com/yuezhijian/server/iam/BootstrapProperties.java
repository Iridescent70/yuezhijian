package com.yuezhijian.server.iam;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.bootstrap")
public record BootstrapProperties(boolean enabled, String username, String password, String fullName) {
    public BootstrapProperties {
        if (username == null || username.isBlank()) {
            username = "admin";
        }
        if (password == null || password.length() < 12) {
            throw new IllegalArgumentException("APP_BOOTSTRAP_PASSWORD至少需要12位");
        }
        if (fullName == null || fullName.isBlank()) {
            fullName = "系统管理员";
        }
    }
}
