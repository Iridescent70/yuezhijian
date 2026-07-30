package com.yuezhijian.server.asset;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CardRefundPayment(
        long paymentMethodId,
        String paymentMethodName,
        BigDecimal amount,
        String status,
        String externalRefundReference,
        LocalDateTime completedAt) {}
