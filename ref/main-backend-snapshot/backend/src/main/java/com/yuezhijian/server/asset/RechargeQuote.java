package com.yuezhijian.server.asset;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record RechargeQuote(
        long id,
        String quoteNo,
        long memberId,
        BigDecimal rechargeAmount,
        BigDecimal giftAmount,
        BigDecimal creditAmount,
        long paymentMethodId,
        String paymentMethodName,
        LocalDateTime expiresAt,
        boolean used) {}
