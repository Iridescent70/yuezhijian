package com.yuezhijian.server.masterdata;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record CreatePositionRequest(
        @NotBlank @Size(max = 64) String code,
        @NotBlank @Size(max = 100) String name,
        @Min(0) @Max(999) int level,
        @NotNull @DecimalMin("0") @DecimalMax("1") @Digits(integer = 1, fraction = 6)
        BigDecimal defaultServiceRate,
        @NotNull @DecimalMin("0") @DecimalMax("1") @Digits(integer = 1, fraction = 6)
        BigDecimal defaultSalesRate) {
}
