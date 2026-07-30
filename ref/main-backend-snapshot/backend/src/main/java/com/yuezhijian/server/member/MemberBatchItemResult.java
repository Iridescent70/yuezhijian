package com.yuezhijian.server.member;

public record MemberBatchItemResult(
        long memberId,
        String memberNo,
        String memberName,
        String status,
        String message) {
}
