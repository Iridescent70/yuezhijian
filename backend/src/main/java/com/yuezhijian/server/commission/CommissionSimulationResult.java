package com.yuezhijian.server.commission;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record CommissionSimulationResult(
        long planId,
        String planCode,
        String planName,
        int planRuleVersion,
        String scene,
        String calculationMode,
        long employeeId,
        String employeeName,
        Long positionId,
        String positionName,
        long storeId,
        String storeName,
        LocalDate businessDate,
        BigDecimal performanceAmount,
        int itemCount,
        BigDecimal commissionAmount,
        boolean applicable,
        List<String> calculationSteps,
        List<String> warnings) {
}
