package com.yuezhijian.server.asset;

import java.math.BigDecimal;

record MemberCardBalanceRow(
        long id,
        long serviceId,
        String serviceCode,
        String serviceName,
        BigDecimal totalTimes,
        BigDecimal remainingTimes,
        BigDecimal frozenTimes,
        BigDecimal deductTimes,
        byte[] rowVersion) {}
