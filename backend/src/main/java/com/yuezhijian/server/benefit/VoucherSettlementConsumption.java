package com.yuezhijian.server.benefit;

import java.math.BigDecimal;

public record VoucherSettlementConsumption(
        long billId,
        long memberId,
        long voucherCodeId,
        BigDecimal amount,
        String voucherVersion,
        String displayName,
        long operatorId) {}
