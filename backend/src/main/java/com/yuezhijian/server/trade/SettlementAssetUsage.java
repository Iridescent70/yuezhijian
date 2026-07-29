package com.yuezhijian.server.trade;

import java.math.BigDecimal;

public record SettlementAssetUsage(
        String assetType,
        long memberId,
        Long voucherCodeId,
        Long memberCardId,
        Long memberCardBalanceId,
        Long billLineId,
        Long serviceId,
        BigDecimal quantity,
        BigDecimal amount,
        String assetVersion,
        String displayName) {}
