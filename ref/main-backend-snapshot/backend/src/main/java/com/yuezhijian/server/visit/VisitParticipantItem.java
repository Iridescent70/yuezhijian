package com.yuezhijian.server.visit;

import java.time.LocalDateTime;

public record VisitParticipantItem(
        long id,
        Long employeeId,
        String employeeName,
        String serviceSummary,
        String status,
        LocalDateTime completedAt) {
}
