package com.yuezhijian.server.commission;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CommissionLedgerDraft(
        String ledgerNo,
        long employeeId,
        long storeId,
        String commissionType,
        String sourceType,
        long sourceId,
        String sourceNo,
        Long sourceLineId,
        String sourceLineName,
        BigDecimal baseAmount,
        BigDecimal rate,
        BigDecimal commissionAmount,
        String calculationStatus,
        Long planId,
        String planName,
        Integer planRuleVersion,
        String formulaSnapshot,
        LocalDateTime occurredAt,
        String correlationId,
        Long reversedLedgerId,
        long createdBy) {
}
