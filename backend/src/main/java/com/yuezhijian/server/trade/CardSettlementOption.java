package com.yuezhijian.server.trade;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CardSettlementOption(
        long billLineId,
        String billLineName,
        long memberCardId,
        String cardNo,
        String cardTypeName,
        long memberCardBalanceId,
        BigDecimal remainingTimes,
        BigDecimal deductTimes,
        BigDecimal requiredTimes,
        LocalDateTime expiresAt,
        boolean recommended) {}
