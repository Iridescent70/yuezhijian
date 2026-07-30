package com.yuezhijian.server.trade;

import java.math.BigDecimal;

public record ProtectedBillRow(
        String billNo,
        Long appointmentId,
        Long memberId,
        String guestName,
        String mobileCiphertext,
        String mobileHash,
        String mobileLast4,
        long storeId,
        String sourceType,
        int personCount,
        BigDecimal originalAmount,
        BigDecimal receivableAmount,
        String status,
        String note,
        String idempotencyKey,
        long operatorId) {
}
