package com.yuezhijian.server.notification;

import java.time.LocalDateTime;
import java.util.List;

public record NotificationTemplate(
        long id,
        String eventCode,
        String eventName,
        String channel,
        String titleTemplate,
        String bodyTemplate,
        List<String> variables,
        String status,
        LocalDateTime updatedAt,
        Long updatedBy,
        String updatedByName,
        String version) {
    public NotificationTemplate {
        variables = List.copyOf(variables);
    }
}
