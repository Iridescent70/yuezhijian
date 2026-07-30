package com.yuezhijian.server.trade;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record QuoteRow(
        long id,
        String quoteNo,
        long billId,
        String billVersion,
        BigDecimal receivableAmount,
        BigDecimal paymentTotal,
        BigDecimal assetAmount,
        BigDecimal externalPaymentAmount,
        BigDecimal changeAmount,
        BigDecimal differenceAmount,
        LocalDateTime expiresAt,
        boolean used) {
}
