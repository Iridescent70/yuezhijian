package com.yuezhijian.server.trade;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record BillAssetUsageItem(
        long id,
        String assetType,
        long memberId,
        Long voucherCodeId,
        Long memberCardId,
        Long memberCardBalanceId,
        Long billLineId,
        Long serviceId,
        BigDecimal quantity,
        BigDecimal amount,
        Long assetLedgerId,
        String displayName,
        LocalDateTime createdAt) {}
