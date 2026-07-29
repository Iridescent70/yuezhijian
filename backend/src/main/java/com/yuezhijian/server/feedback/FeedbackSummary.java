package com.yuezhijian.server.feedback;

import java.time.LocalDateTime;

public record FeedbackSummary(
        long id,
        String feedbackNo,
        long visitTaskId,
        long visitRecordId,
        long memberId,
        String memberName,
        String maskedMobile,
        long billId,
        String billNo,
        long storeId,
        String storeName,
        String channel,
        Integer score,
        String content,
        String complaintType,
        String status,
        Long handlerId,
        String handlerName,
        String handleResult,
        LocalDateTime handledAt,
        LocalDateTime resolvedAt,
        LocalDateTime closedAt,
        int actionCount,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
