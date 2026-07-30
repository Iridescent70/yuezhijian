package com.yuezhijian.server.feedback;

import java.time.LocalDateTime;

public record FeedbackDraft(
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
        Integer score,
        String content,
        LocalDateTime createdAt,
        int dueHours,
        LocalDateTime dueAt,
        long createdBy) {
}
