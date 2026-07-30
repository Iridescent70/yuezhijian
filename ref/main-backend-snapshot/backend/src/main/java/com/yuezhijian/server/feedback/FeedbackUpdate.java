package com.yuezhijian.server.feedback;

import java.time.LocalDateTime;

public record FeedbackUpdate(
        long id,
        String expectedStatus,
        String status,
        Long handlerId,
        String handleResult,
        String actionType,
        String content,
        Integer dueHours,
        LocalDateTime dueAt,
        long operatorId) {
}
