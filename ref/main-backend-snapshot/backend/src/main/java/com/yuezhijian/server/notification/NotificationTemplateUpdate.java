package com.yuezhijian.server.notification;

public record NotificationTemplateUpdate(
        long id,
        String eventName,
        String titleTemplate,
        String bodyTemplate,
        String variablesCsv,
        String status,
        String version,
        long operatorId) {
}
