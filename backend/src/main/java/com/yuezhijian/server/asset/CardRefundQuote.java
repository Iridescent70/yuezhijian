package com.yuezhijian.server.asset;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record CardRefundQuote(
        long id,
        String quoteNo,
        long memberCardId,
        String cardNo,
        String cardTypeName,
        long memberId,
        BigDecimal originalAmount,
        BigDecimal consumedRepriceAmount,
        BigDecimal feeAmount,
        BigDecimal refundAmount,
        String cardVersion,
        List<CardConsumptionRepriceItem> items,
        LocalDateTime expiresAt,
        boolean used) {
    public CardRefundQuote { items = List.copyOf(items); }
}
