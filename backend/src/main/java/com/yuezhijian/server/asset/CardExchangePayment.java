package com.yuezhijian.server.asset;

import java.math.BigDecimal;

public record CardExchangePayment(
        long paymentMethodId,
        String paymentMethodName,
        BigDecimal amount,
        String externalReference) {}
