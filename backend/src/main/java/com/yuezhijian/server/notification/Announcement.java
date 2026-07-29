package com.yuezhijian.server.notification;

import java.time.LocalDateTime;
import java.util.List;

public record Announcement(
        long id,
        String notificationNo,
        String title,
        String body,
        String scopeType,
        List<Long> storeIds,
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
    public Announcement {
        storeIds = List.copyOf(storeIds);
    }
}
