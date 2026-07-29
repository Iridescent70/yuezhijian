package com.yuezhijian.server.member;

public record MemberTagOption(
        long id,
        String code,
        String name,
        String source,
        String color,
        boolean negative) {
}
