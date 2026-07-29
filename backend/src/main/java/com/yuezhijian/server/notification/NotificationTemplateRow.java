package com.yuezhijian.server.notification;

import java.time.LocalDateTime;

public record NotificationTemplateRow(
        long id,
        String eventCode,
        String eventName,
        String channel,
        String titleTemplate,
        String bodyTemplate,
        String variablesCsv,
        String status,
        LocalDateTime updatedAt,
        Long updatedBy,
        String updatedByName,
        String version) {
}
