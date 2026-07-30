package com.yuezhijian.server.inventory;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record CreateGiftRequest(
        @NotBlank @Size(max = 64) String code,
        @NotBlank @Size(max = 200) String name,
        @Positive long categoryId,
        @Positive long unitId,
        @Positive @Max(100000000) int pointPrice,
        @DecimalMin("0") @Digits(integer = 15, fraction = 4) BigDecimal costPrice,
        @DecimalMin("0") @Digits(integer = 15, fraction = 4) BigDecimal lowStockThreshold,
        @Size(max = 1000) String description) {
}
