package com.yuezhijian.server.trade;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record BillSummary(
        long id,
        String billNo,
        Long appointmentId,
        Long memberId,
        String customerName,
        String maskedMobile,
        long storeId,
        String storeName,
        String sourceType,
        int personCount,
        BigDecimal originalAmount,
        BigDecimal discountAmount,
        BigDecimal receivableAmount,
        BigDecimal receivedAmount,
        BigDecimal changeAmount,
        String status,
        String note,
        LocalDateTime settledAt,
        LocalDateTime createdAt,
        String version) {
}
