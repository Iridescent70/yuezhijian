package com.yuezhijian.server.asset;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record CardExchangePaymentRequest(
        @Positive long paymentMethodId,
        @NotNull @DecimalMin(value = "0.0001") BigDecimal amount,
        @Size(max = 128) String externalReference) {}
