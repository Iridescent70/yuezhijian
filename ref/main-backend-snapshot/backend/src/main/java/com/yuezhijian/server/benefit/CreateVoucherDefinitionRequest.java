package com.yuezhijian.server.benefit;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record CreateVoucherDefinitionRequest(
        @NotBlank @Size(max = 64) String code,
        @NotBlank @Size(max = 200) String name,
        @NotBlank String benefitType,
        @NotNull @DecimalMin("0.00") BigDecimal faceAmount,
        @NotNull @DecimalMin("0.000001") BigDecimal discountRate,
        @NotNull @DecimalMin("0.00") BigDecimal minSpend,
        @Min(1) @Max(3650) int validDays,
        @Size(max = 1000) String commissionRule) {}
