package com.yuezhijian.server.asset;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CardExchangeQuote(
        long id,
        String quoteNo,
        long oldCardId,
        String oldCardNo,
        String oldCardTypeName,
        long targetCardTypeId,
        String targetCardTypeName,
        String targetCardTypeVersion,
        BigDecimal oldRemainingTimes,
        BigDecimal oldRemainingValue,
        BigDecimal newCardValue,
        BigDecimal differenceAmount,
        String oldCardVersion,
        LocalDateTime expiresAt,
        boolean used) {}
