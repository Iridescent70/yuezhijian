package com.yuezhijian.server.visit;

import java.time.LocalDateTime;

public record VisitRecordItem(
        long id,
        long participantId,
        long employeeId,
        String employeeName,
        String resultCode,
        Integer satisfactionScore,
        boolean complaintFlag,
        String content,
        LocalDateTime nextFollowAt,
        LocalDateTime createdAt,
        long createdBy,
        String createdByName) {
}
