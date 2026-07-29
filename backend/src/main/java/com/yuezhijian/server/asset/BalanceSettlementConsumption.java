package com.yuezhijian.server.asset;

import java.math.BigDecimal;

public record BalanceSettlementConsumption(
        long billId,
        long memberId,
        long storeId,
        BigDecimal amount,
        String accountVersion,
        String displayName,
        long operatorId) {}
