package com.yuezhijian.server.trade;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record BillAssetUsageItem(
        long id,
        String assetType,
        Long memberCardId,
        Long billLineId,
        BigDecimal quantity,
        BigDecimal amount,
        String displayName,
        LocalDateTime createdAt) {}
