package com.yuezhijian.server.asset;

import java.math.BigDecimal;

public record MemberCardBalanceItem(
        long id,
        long serviceId,
        String serviceCode,
        String serviceName,
        BigDecimal totalTimes,
        BigDecimal remainingTimes,
        BigDecimal frozenTimes,
        BigDecimal deductTimes,
        String version) {}
