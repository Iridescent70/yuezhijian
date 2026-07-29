package com.yuezhijian.server.benefit;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record VoucherCodeSummary(
        long id,
        String code,
        long voucherId,
        String voucherCode,
        String voucherName,
        String benefitType,
        BigDecimal faceAmount,
        BigDecimal discountRate,
        BigDecimal minSpend,
        Long memberId,
        String memberName,
        LocalDateTime validFrom,
        LocalDateTime validUntil,
        String status,
        LocalDateTime boundAt,
        LocalDateTime redeemedAt,
        Long redeemedBillId,
        String version) {}
