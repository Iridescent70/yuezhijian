package com.yuezhijian.server.asset;

import java.math.BigDecimal;
import java.util.List;

public record CardTypeDetail(
        long id,
        String code,
        String name,
        BigDecimal salePrice,
        BigDecimal listPrice,
        BigDecimal totalTimes,
        int validDays,
        BigDecimal purchaseThreshold,
        String instructions,
        int autoRemindDays,
        List<Long> storeIds,
        List<CardServiceRule> serviceRules,
        String status,
        String version) {}
