package com.yuezhijian.server.visit;

public record VisitParticipantDraft(
        Long employeeId,
        String employeeName,
        String serviceSummary) {
}
