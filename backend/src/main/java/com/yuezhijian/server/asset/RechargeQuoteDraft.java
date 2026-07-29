package com.yuezhijian.server.asset;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record RechargeQuoteDraft(
        String quoteNo,
        long memberId,
        BigDecimal rechargeAmount,
        BigDecimal giftAmount,
        long paymentMethodId,
        String paymentMethodName,
        LocalDateTime expiresAt,
        long operatorId) {}
