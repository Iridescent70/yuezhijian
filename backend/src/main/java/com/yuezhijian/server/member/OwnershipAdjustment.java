package com.yuezhijian.server.member;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record OwnershipAdjustment(
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
        Map<String, Object> shareRule,
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
    public OwnershipAdjustment {
        shareRule = Collections.unmodifiableMap(new LinkedHashMap<>(shareRule));
    }
}
