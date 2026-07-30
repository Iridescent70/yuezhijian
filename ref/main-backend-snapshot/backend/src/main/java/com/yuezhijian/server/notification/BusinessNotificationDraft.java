package com.yuezhijian.server.notification;

public record BusinessNotificationDraft(
        String notificationNo,
        String messageType,
        String eventCode,
        String title,
        String body,
        long storeId,
        String businessType,
        long businessId,
        String route,
        int priority,
        long operatorId) {
}
