package com.yuezhijian.server.asset;

import java.math.BigDecimal;
import java.time.LocalDateTime;

record CardExchangeRow(
        long id,
        String exchangeNo,
        long oldCardId,
        long newCardId,
        BigDecimal oldRemainingValue,
        BigDecimal newCardValue,
        BigDecimal differenceAmount,
        LocalDateTime executedAt) {}
