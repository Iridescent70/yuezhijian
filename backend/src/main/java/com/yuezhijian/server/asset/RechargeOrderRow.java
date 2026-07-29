package com.yuezhijian.server.asset;

import java.math.BigDecimal;
import java.time.LocalDateTime;

record RechargeOrderRow(
        long id,
        String rechargeNo,
        String quoteNo,
        long memberId,
        long storeId,
        String storeName,
        BigDecimal rechargeAmount,
        BigDecimal giftAmount,
        BigDecimal creditAmount,
        long paymentMethodId,
        String paymentMethodName,
        String externalReference,
        Long salesEmployeeId,
        String status,
        LocalDateTime confirmedAt,
        LocalDateTime cancelledAt,
        String cancelReason,
        LocalDateTime createdAt,
        byte[] rowVersion) {}
