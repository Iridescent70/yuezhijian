package com.yuezhijian.server.appointment;

import java.time.LocalDateTime;

public record AppointmentHistoryItem(
        long id,
        String fromStatus,
        String toStatus,
        String reasonCode,
        String note,
        LocalDateTime occurredAt,
        long operatorId) {
}
