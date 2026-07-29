package com.yuezhijian.server.benefit;

import java.time.LocalDateTime;
import java.util.List;

public record VoucherIssueDraft(
        String batchNo,
        VoucherDefinition definition,
        int count,
        Long memberId,
        String memberName,
        LocalDateTime validFrom,
        LocalDateTime validUntil,
        List<String> codes,
        String idempotencyKey,
        long operatorId) {
    public VoucherIssueDraft { codes = List.copyOf(codes); }
}
