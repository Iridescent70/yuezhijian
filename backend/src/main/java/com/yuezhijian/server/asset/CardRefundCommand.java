package com.yuezhijian.server.asset;

import java.math.BigDecimal;

public record CardRefundCommand(
        long reversalId,
        long usageId,
        long memberId,
        long memberCardId,
        long memberCardBalanceId,
        long serviceId,
        BigDecimal times,
        BigDecimal amount,
        Long originalLedgerId,
        String note,
        long operatorId) {}
