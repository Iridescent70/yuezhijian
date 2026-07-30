package com.yuezhijian.server.notification;

public record NewNotificationTemplate(
        String eventCode,
        String eventName,
        String titleTemplate,
        String bodyTemplate,
        String variablesCsv,
        String status,
        long operatorId) {
}
