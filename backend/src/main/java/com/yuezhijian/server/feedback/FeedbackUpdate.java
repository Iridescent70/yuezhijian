package com.yuezhijian.server.feedback;

public record FeedbackUpdate(
        long id,
        String expectedStatus,
        String status,
        Long handlerId,
        String handleResult,
        String actionType,
        String content,
        long operatorId) {
}
