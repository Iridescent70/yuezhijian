package com.yuezhijian.server.asset;

import java.math.BigDecimal;
import java.time.LocalDateTime;

record BalanceAccountRow(
        long accountId,
        long memberId,
        BigDecimal availableBalance,
        BigDecimal frozenBalance,
        BigDecimal totalRecharged,
        LocalDateTime lastTransactionAt,
        byte[] rowVersion) {}
