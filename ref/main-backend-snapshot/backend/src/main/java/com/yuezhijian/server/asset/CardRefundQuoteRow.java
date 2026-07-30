package com.yuezhijian.server.asset;

import java.math.BigDecimal;
import java.time.LocalDateTime;

record CardRefundQuoteRow(
        long id,
        String quoteNo,
        long memberCardId,
        String cardNo,
        String cardTypeName,
        long memberId,
        BigDecimal originalAmount,
        BigDecimal consumedRepriceAmount,
        BigDecimal feeAmount,
        BigDecimal refundAmount,
        String cardVersion,
        LocalDateTime expiresAt,
        boolean used) {}
