package com.yuezhijian.server.asset;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record CardExchangeResult(
        long exchangeId,
        String exchangeNo,
        MemberCardSummary oldCard,
        MemberCardSummary newCard,
        BigDecimal oldRemainingValue,
        BigDecimal newCardValue,
        BigDecimal differenceAmount,
        List<CardExchangePayment> payments,
        LocalDateTime executedAt) {
    public CardExchangeResult { payments = List.copyOf(payments); }
}
