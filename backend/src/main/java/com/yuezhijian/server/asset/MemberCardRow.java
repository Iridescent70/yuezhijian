package com.yuezhijian.server.asset;

import java.math.BigDecimal;
import java.time.LocalDateTime;

record MemberCardRow(
        long id,
        String cardNo,
        long memberId,
        long cardTypeId,
        String cardTypeCode,
        String cardTypeName,
        long purchaseStoreId,
        String purchaseStoreName,
        BigDecimal purchasePrice,
        BigDecimal totalTimes,
        BigDecimal remainingTimes,
        BigDecimal frozenTimes,
        LocalDateTime startedAt,
        LocalDateTime expiresAt,
        String status,
        byte[] rowVersion) {}
