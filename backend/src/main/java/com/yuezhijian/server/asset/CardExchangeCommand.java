package com.yuezhijian.server.asset;

import java.time.LocalDateTime;
import java.util.List;

public record CardExchangeCommand(
        String exchangeNo,
        CardExchangeQuote quote,
        CardTypeDetail targetCardType,
        long memberId,
        long storeId,
        String storeName,
        Long employeeId,
        List<CardExchangePayment> payments,
        LocalDateTime startedAt,
        String idempotencyKey,
        long operatorId) {
    public CardExchangeCommand { payments = List.copyOf(payments); }
}
