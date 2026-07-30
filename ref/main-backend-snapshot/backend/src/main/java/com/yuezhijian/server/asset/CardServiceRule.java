package com.yuezhijian.server.asset;

import java.math.BigDecimal;

public record CardServiceRule(
        long serviceId,
        String serviceCode,
        String serviceName,
        BigDecimal includedTimes,
        BigDecimal deductTimes,
        int priority) {}
