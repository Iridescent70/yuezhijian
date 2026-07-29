package com.yuezhijian.server.asset;

import java.math.BigDecimal;

record CardTypeRow(
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
        String status,
        byte[] rowVersion) {}
