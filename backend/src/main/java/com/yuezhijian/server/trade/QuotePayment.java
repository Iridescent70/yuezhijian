package com.yuezhijian.server.trade;

import java.math.BigDecimal;

public record QuotePayment(
        long paymentMethodId,
        String paymentMethodCode,
        String paymentMethodName,
        BigDecimal amount,
        String externalReference) {
}
