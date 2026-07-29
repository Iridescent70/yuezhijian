package com.yuezhijian.server.trade;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record SettlementQuoteDraft(
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
        long operatorId) {
    public SettlementQuoteDraft {
        payments = List.copyOf(payments);
        assets = List.copyOf(assets);
    }
}
