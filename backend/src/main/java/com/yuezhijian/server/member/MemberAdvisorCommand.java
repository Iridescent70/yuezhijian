package com.yuezhijian.server.member;

public record MemberAdvisorCommand(
        long memberId,
        long ownerStoreId,
        Long oldAdvisorEmployeeId,
        Long newAdvisorEmployeeId,
        String version,
        long operatorId,
        String changeSource) {
}
