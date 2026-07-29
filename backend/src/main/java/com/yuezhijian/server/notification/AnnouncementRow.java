package com.yuezhijian.server.notification;

import java.time.LocalDateTime;

public record AnnouncementRow(
        long id,
        String notificationNo,
        String title,
        String body,
        String scopeType,
        LocalDateTime validFrom,
        LocalDateTime validTo,
        int priority,
        boolean pinned,
        String status,
        LocalDateTime publishedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        long updatedBy,
        String updatedByName,
        String version) {
}
