package com.yuezhijian.server.member;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record OwnershipAdjustmentRow(
        long id,
        String adjustmentNo,
        long memberId,
        String memberNo,
        String memberName,
        long oldStoreId,
        String oldStoreName,
        long newStoreId,
        String newStoreName,
        LocalDate effectiveDate,
        String shareRuleJson,
        String reason,
        String approvalStatus,
        String executionStatus,
        long requestedBy,
        LocalDateTime requestedAt,
        Long reviewedBy,
        LocalDateTime reviewedAt,
        String reviewComment,
        LocalDateTime appliedAt,
        String executionMessage,
        String version) {
}
