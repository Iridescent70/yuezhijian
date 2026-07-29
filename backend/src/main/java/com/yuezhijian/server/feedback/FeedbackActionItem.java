package com.yuezhijian.server.feedback;

import java.time.LocalDateTime;

public record FeedbackActionItem(
        long id,
        String actionType,
        String fromStatus,
        String toStatus,
        Long handlerId,
        String handlerName,
        String content,
        LocalDateTime createdAt,
        long createdBy,
        String createdByName) {
}
