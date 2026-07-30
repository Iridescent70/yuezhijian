package com.yuezhijian.server.asset;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;

public record CreateCardTypeRequest(
        @NotBlank @Size(max = 64) String code,
        @NotBlank @Size(max = 200) String name,
        @NotNull @DecimalMin("0.00") BigDecimal salePrice,
        @NotNull @DecimalMin("0.00") BigDecimal listPrice,
        @NotNull @DecimalMin("0.0001") BigDecimal totalTimes,
        @Min(1) @Max(3650) int validDays,
        @DecimalMin("0.00") BigDecimal purchaseThreshold,
        String instructions,
        @Min(0) @Max(365) int autoRemindDays,
        @NotEmpty List<Long> storeIds,
        @NotEmpty List<@Valid CardServiceRuleRequest> serviceRules) {}
