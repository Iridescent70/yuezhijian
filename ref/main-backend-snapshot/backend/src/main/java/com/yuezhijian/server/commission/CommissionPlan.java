package com.yuezhijian.server.commission;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CommissionPlan(
        long id,
        String code,
        String name,
        String scene,
        String calculationMode,
        BigDecimal rate,
        BigDecimal fixedAmount,
        Long storeId,
        String storeName,
        Long positionId,
        String positionName,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        String status,
        int ruleVersion,
        String version) {
}
