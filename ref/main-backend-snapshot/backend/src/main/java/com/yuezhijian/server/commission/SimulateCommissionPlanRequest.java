package com.yuezhijian.server.commission;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDate;

public record SimulateCommissionPlanRequest(
        @Positive long employeeId,
        @Positive long storeId,
        @NotNull LocalDate businessDate,
        @NotNull @DecimalMin("0.0000") @Digits(integer = 15, fraction = 4) BigDecimal performanceAmount,
        @NotNull @Min(1) @Max(10000) Integer itemCount) {
}
