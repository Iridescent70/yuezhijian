package com.yuezhijian.server.member;

public record MemberStatusCommand(
        long id,
        String fromStatus,
        String toStatus,
        String reason,
        String version,
        long operatorId) {
}
