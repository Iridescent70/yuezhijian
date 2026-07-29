package com.yuezhijian.server.asset;

import java.math.BigDecimal;

public record PointSettlementConsumption(
        long billId,
        long memberId,
        int points,
        BigDecimal amount,
        String accountVersion,
        String displayName,
        long operatorId) {}
