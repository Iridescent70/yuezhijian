package com.yuezhijian.server.visit;

import java.time.LocalDateTime;

public record VisitRecordDraft(
        long taskId,
        long participantId,
        long employeeId,
        String employeeName,
        String resultCode,
        Integer satisfactionScore,
        boolean complaintFlag,
        String content,
        LocalDateTime nextFollowAt,
        boolean completesParticipant,
        long createdBy) {
}
