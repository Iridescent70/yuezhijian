package com.yuezhijian.server.asset;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CardConsumptionRepriceItem(
        long cardLedgerId,
        long billId,
        String billNo,
        long serviceId,
        String serviceName,
        LocalDateTime consumedAt,
        BigDecimal originalAmount) {}
