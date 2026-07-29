package com.yuezhijian.server.asset;

public record CardRefundSubmission(
        String requestNo,
        CardRefundQuote quote,
        String memberName,
        Long refundMethodId,
        String refundMethodName,
        boolean refundMethodRequiresReference,
        long storeId,
        String storeName,
        Long employeeId,
        String reason,
        String idempotencyKey,
        long operatorId) {}
