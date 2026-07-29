package com.yuezhijian.server.feedback;

public record FeedbackQuery(
        Long storeId,
        Long handlerId,
        Integer score,
        String status,
        String keyword) {
}
