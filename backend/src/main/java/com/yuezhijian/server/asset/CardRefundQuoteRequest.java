package com.yuezhijian.server.asset;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record CardRefundQuoteRequest(
        @NotNull @DecimalMin("0.0000") BigDecimal feeAmount) {}
