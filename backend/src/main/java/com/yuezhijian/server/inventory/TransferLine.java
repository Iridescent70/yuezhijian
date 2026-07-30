package com.yuezhijian.server.inventory;

import java.math.BigDecimal;

public record TransferLine(
        long id,
        long giftId,
        String giftCode,
        String giftName,
        String unitName,
        int unitDecimalPlaces,
        BigDecimal quantity,
        String note,
        Long sourceLedgerId,
        Long targetLedgerId) {
}
