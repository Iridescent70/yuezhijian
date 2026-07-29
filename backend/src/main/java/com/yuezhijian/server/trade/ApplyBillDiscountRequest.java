package com.yuezhijian.server.trade;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record ApplyBillDiscountRequest(
        @NotBlank String discountType,
        @NotNull @DecimalMin("0.0000") BigDecimal value,
        @NotBlank @Size(max = 500) String reason,
        @NotBlank String version) {}
