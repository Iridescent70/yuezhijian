package com.yuezhijian.server.benefit;

public record VoucherRefundCommand(
        long reversalId,
        long billId,
        long usageId,
        long voucherCodeId,
        long redeemLedgerId,
        String note,
        long operatorId) {}
