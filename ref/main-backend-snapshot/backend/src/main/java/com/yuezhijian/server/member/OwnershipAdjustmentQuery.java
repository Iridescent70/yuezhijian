package com.yuezhijian.server.member;

public record OwnershipAdjustmentQuery(
        Long memberId,
        String approvalStatus,
        String executionStatus) {
}
