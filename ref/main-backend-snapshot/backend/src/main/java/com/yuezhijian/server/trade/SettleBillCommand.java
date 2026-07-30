package com.yuezhijian.server.trade;

public record SettleBillCommand(
        long billId,
        SettlementQuote quote,
        String idempotencyKey,
        long operatorId) {
}
