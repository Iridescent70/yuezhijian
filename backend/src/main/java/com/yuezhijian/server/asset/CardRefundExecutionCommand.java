package com.yuezhijian.server.asset;

public record CardRefundExecutionCommand(
        CardRefundRequestDetail request,
        String version,
        String externalRefundReference,
        String idempotencyKey,
        long operatorId) {}
