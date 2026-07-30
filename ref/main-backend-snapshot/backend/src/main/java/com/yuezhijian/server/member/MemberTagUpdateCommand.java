package com.yuezhijian.server.member;

import java.util.List;

public record MemberTagUpdateCommand(
        long memberId,
        List<Long> addIds,
        List<Long> removeIds,
        String version,
        long operatorId) {
    public MemberTagUpdateCommand {
        addIds = List.copyOf(addIds);
        removeIds = List.copyOf(removeIds);
    }
}
