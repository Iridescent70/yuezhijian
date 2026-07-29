package com.yuezhijian.server.asset;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record RechargeQuoteRequest(
        @NotNull @DecimalMin(value = "0.01") BigDecimal rechargeAmount,
        @DecimalMin(value = "0.00") BigDecimal giftAmount,
        @NotNull Long paymentMethodId) {}
