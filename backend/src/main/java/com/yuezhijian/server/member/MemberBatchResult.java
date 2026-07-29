package com.yuezhijian.server.member;

import java.util.List;

public record MemberBatchResult(
        String operation,
        int total,
        int succeeded,
        int skipped,
        int failed,
        List<MemberBatchItemResult> items) {
    public MemberBatchResult {
        items = List.copyOf(items);
    }

    public static MemberBatchResult of(String operation, List<MemberBatchItemResult> items) {
        int succeeded = (int) items.stream().filter(item -> "SUCCESS".equals(item.status())).count();
        int skipped = (int) items.stream().filter(item -> "SKIPPED".equals(item.status())).count();
        return new MemberBatchResult(
                operation, items.size(), succeeded, skipped, items.size() - succeeded - skipped, items);
    }
}
