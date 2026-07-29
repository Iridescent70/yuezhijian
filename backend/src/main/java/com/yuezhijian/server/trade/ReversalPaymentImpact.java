package com.yuezhijian.server.trade;

import java.math.BigDecimal;

public record ReversalPaymentImpact(
        long paymentId,
        String paymentMethodName,
        BigDecimal amount,
        String status) {}
