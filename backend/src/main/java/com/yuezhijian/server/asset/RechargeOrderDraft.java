package com.yuezhijian.server.asset;

public record RechargeOrderDraft(
        String rechargeNo,
        RechargeQuote quote,
        long storeId,
        String storeName,
        Long salesEmployeeId,
        String externalReference,
        String idempotencyKey,
        long operatorId) {}
