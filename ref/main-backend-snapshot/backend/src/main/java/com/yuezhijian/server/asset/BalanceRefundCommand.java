package com.yuezhijian.server.asset;

import java.math.BigDecimal;

public record BalanceRefundCommand(
        long reversalId,
        long usageId,
        long memberId,
        long storeId,
        BigDecimal amount,
        Long originalLedgerId,
        String note,
        long operatorId) {}
