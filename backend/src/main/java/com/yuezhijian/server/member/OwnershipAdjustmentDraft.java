package com.yuezhijian.server.member;

import java.time.LocalDate;

public record OwnershipAdjustmentDraft(
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
        String memberVersion,
        long requestedBy) {
}
