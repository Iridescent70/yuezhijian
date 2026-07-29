package com.yuezhijian.server.benefit;

import java.math.BigDecimal;

public record VoucherDefinition(
        long id,
        String code,
        String name,
        String benefitType,
        BigDecimal faceAmount,
        BigDecimal discountRate,
        BigDecimal minSpend,
        int validDays,
        String commissionRule,
        String status,
        String version) {}
