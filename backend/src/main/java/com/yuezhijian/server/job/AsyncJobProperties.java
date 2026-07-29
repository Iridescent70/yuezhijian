package com.yuezhijian.server.job;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.jobs")
public record AsyncJobProperties(int leaseMinutes, int maxAttempts) {
    public AsyncJobProperties {
        if (leaseMinutes <= 0) leaseMinutes = 30;
        if (maxAttempts <= 0) maxAttempts = 3;
        if (maxAttempts > 10) maxAttempts = 10;
    }
}
