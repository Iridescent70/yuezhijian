package com.yuezhijian.server.visit;

import java.time.LocalDateTime;
import java.util.List;

public record VisitTaskDraft(
        String taskNo,
        long memberId,
        long billId,
        String billNo,
        String customerName,
        String maskedMobile,
        long storeId,
        String storeName,
        LocalDateTime dueAt,
        LocalDateTime settledAt,
        List<VisitParticipantDraft> participants,
        long createdBy) {
    public VisitTaskDraft {
        participants = List.copyOf(participants);
    }
}
