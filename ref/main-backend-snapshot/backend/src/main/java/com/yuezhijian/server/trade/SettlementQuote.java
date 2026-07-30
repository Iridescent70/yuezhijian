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
        BigDecimal assetAmount,
        BigDecimal externalPaymentAmount,
        BigDecimal changeAmount,
        BigDecimal differenceAmount,
        List<QuotePayment> payments,
        List<SettlementAssetUsage> assets,
        LocalDateTime expiresAt,
        boolean used) {
    public SettlementQuote {
        payments = List.copyOf(payments);
        assets = List.copyOf(assets);
    }
}
