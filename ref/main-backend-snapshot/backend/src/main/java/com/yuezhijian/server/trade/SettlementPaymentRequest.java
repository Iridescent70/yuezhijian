package com.yuezhijian.server.trade;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record SettlementPaymentRequest(
        @NotNull Long paymentMethodId,
        @NotNull @DecimalMin("0.0001") BigDecimal amount,
        @Size(max = 128) String externalReference) {
}
