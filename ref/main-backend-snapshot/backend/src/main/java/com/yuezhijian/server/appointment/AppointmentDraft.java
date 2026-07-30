package com.yuezhijian.server.appointment;

import java.time.LocalDateTime;
import java.util.List;

public record AppointmentDraft(
        String appointmentNo,
        Long memberId,
        String guestName,
        String guestMobile,
        long storeId,
        String sourceType,
        String appointmentType,
        LocalDateTime startAt,
        LocalDateTime endAt,
        int personCount,
        long employeeId,
        long workstationId,
        List<AppointmentServiceLine> services,
        boolean designated,
        String note,
        String idempotencyKey,
        long operatorId) {
    public AppointmentDraft {
        services = List.copyOf(services);
    }
}
