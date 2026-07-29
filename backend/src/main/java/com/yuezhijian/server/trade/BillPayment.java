package com.yuezhijian.server.trade;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record BillPayment(
        long id,
        String paymentNo,
        long paymentMethodId,
        String paymentMethodName,
        BigDecimal amount,
        String status,
        String externalReference,
        LocalDateTime paidAt) {
}
