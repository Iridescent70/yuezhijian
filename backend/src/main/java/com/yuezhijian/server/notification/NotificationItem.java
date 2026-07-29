package com.yuezhijian.server.notification;

import java.time.LocalDateTime;

public record NotificationItem(
        long id,
        String notificationNo,
        String messageType,
        String eventCode,
        String title,
        String body,
        String businessType,
        Long businessId,
        String route,
        int priority,
        boolean pinned,
        LocalDateTime publishedAt,
        LocalDateTime validTo,
        boolean read,
        LocalDateTime readAt) {
}
