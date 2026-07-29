package com.yuezhijian.server.asset;

import java.math.BigDecimal;
import java.util.List;

public record CardSaleResult(
        long orderId,
        String orderNo,
        BigDecimal totalAmount,
        List<MemberCardSummary> cards) {}
