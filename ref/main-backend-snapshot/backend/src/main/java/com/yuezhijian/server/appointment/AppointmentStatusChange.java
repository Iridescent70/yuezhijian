package com.yuezhijian.server.appointment;

public record AppointmentStatusChange(
        long id,
        String fromStatus,
        String toStatus,
        String version,
        String reasonCode,
        String note,
        Integer personCount,
        long operatorId) {
}
