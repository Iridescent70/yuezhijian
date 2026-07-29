package com.yuezhijian.server.asset;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record CardServiceRuleRequest(
        @NotNull Long serviceId,
        @NotNull @DecimalMin("0.0001") BigDecimal includedTimes,
        @NotNull @DecimalMin("0.0001") BigDecimal deductTimes,
        int priority) {}
