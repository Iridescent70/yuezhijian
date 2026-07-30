package com.yuezhijian.server.trade;

public record ReversalExecutionCommand(
        ReversalDetail reversal,
        String version,
        String idempotencyKey,
        long operatorId) {}
