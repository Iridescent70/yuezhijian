package com.yuezhijian.server.trade;

import java.util.List;

public record BillDraft(
        String billNo,
        Long appointmentId,
        Long memberId,
        String guestName,
        String guestMobile,
        String guestMaskedMobile,
        long storeId,
        String sourceType,
        int personCount,
        String note,
        String idempotencyKey,
        List<BillLineDraft> lines,
        long operatorId) {
    public BillDraft {
        lines = List.copyOf(lines);
    }
}
