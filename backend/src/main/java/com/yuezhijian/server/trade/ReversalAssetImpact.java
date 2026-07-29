package com.yuezhijian.server.trade;

import java.math.BigDecimal;

public record ReversalAssetImpact(
        long usageId,
        String assetType,
        long memberId,
        Long memberCardId,
        Long memberCardBalanceId,
        Long billLineId,
        Long serviceId,
        BigDecimal quantity,
        BigDecimal amount,
        Long assetLedgerId,
        String displayName) {}
