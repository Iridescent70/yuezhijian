package com.yuezhijian.server.trade;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record SettlementQuote(
        String quoteNo,
        long billId,
        String billVersion,
        BigDecimal receivableAmount,
        BigDecimal paymentTotal,
        BigDecimal changeAmount,
        BigDecimal differenceAmount,
        List<QuotePayment> payments,
        LocalDateTime expiresAt,
        boolean used) {
    public SettlementQuote {
        payments = List.copyOf(payments);
    }
}
