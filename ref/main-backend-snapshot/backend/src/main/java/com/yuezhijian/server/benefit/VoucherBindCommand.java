package com.yuezhijian.server.benefit;

public record VoucherBindCommand(
        VoucherCodeSummary voucher,
        long memberId,
        String memberName,
        String idempotencyKey,
        long operatorId) {}
