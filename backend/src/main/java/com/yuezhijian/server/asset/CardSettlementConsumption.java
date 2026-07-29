package com.yuezhijian.server.asset;

import java.math.BigDecimal;

public record CardSettlementConsumption(
        long billId,
        long memberId,
        long memberCardId,
        long memberCardBalanceId,
        long billLineId,
        long serviceId,
        BigDecimal times,
        BigDecimal amount,
        BigDecimal originalAmount,
        String balanceVersion,
        String displayName,
        long operatorId) {}
