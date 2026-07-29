package com.yuezhijian.server.asset;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CardExchangeQuoteDraft(
        String quoteNo,
        MemberCardSummary oldCard,
        CardTypeDetail targetCardType,
        BigDecimal oldRemainingTimes,
        BigDecimal oldRemainingValue,
        BigDecimal differenceAmount,
        LocalDateTime expiresAt,
        long operatorId) {}
