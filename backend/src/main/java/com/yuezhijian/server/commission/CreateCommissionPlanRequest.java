package com.yuezhijian.server.commission;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

public record CreateCommissionPlanRequest(
        @NotBlank @Size(max = 64) String code,
        @NotBlank @Size(max = 200) String name,
        @NotBlank String scene,
        @NotBlank String calculationMode,
        BigDecimal rate,
        BigDecimal fixedAmount,
        Long storeId,
        Long positionId,
        @NotNull LocalDate effectiveFrom,
        LocalDate effectiveTo) {
}
