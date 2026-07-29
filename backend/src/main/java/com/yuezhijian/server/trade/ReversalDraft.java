package com.yuezhijian.server.trade;

public record ReversalDraft(
        String reversalNo,
        BillDetail bill,
        String reason,
        String idempotencyKey,
        long operatorId) {}
