package com.yuezhijian.server.visit;

import java.time.LocalDateTime;

public record VisitTaskSummary(
        long id,
        String taskNo,
        long memberId,
        long billId,
        String billNo,
        String customerName,
        String maskedMobile,
        long storeId,
        String storeName,
        LocalDateTime dueAt,
        String taskType,
        String status,
        boolean overdue,
        boolean complaintFlag,
        int participantCount,
        int completedCount,
        String conclusion,
        LocalDateTime settledAt,
        LocalDateTime completedAt,
        LocalDateTime canceledAt,
        String cancelReason,
        LocalDateTime createdAt) {
}
