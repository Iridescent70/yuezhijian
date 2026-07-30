package com.yuezhijian.server.benefit;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record VoucherSettlementOption(
        long id,
        String code,
        String voucherName,
        String benefitType,
        BigDecimal faceAmount,
        BigDecimal discountRate,
        BigDecimal minSpend,
        BigDecimal previewAmount,
        LocalDateTime validUntil,
        String version) {}
