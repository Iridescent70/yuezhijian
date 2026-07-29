package com.yuezhijian.server.asset;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record CardRefundQuoteDraft(
        String quoteNo,
        MemberCardSummary card,
        BigDecimal originalAmount,
        BigDecimal consumedRepriceAmount,
        BigDecimal feeAmount,
        BigDecimal refundAmount,
        List<CardConsumptionRepriceItem> items,
        LocalDateTime expiresAt,
        long operatorId) {
    public CardRefundQuoteDraft { items = List.copyOf(items); }
}
