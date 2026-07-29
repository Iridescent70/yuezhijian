package com.yuezhijian.server.asset;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record BalanceAccount(
        long memberId,
        BigDecimal availableBalance,
        BigDecimal frozenBalance,
        BigDecimal totalRecharged,
        LocalDateTime lastTransactionAt,
        String version) {}
