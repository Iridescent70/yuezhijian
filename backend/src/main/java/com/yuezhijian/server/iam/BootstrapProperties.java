package com.yuezhijian.server.iam;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.bootstrap")
public record BootstrapProperties(String username, String password) {
    public BootstrapProperties {
        if (username == null || username.isBlank()) {
            username = "admin";
        }
        if (password == null || password.length() < 12) {
            throw new IllegalArgumentException("APP_BOOTSTRAP_PASSWORD至少需要12位");
        }
    }
}
