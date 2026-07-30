package com.yuezhijian.server.appointment;

import java.time.LocalDateTime;

public record ProtectedAppointmentRow(
        String appointmentNo,
        Long memberId,
        String guestName,
        String mobileCiphertext,
        String mobileHash,
        String mobileLast4,
        long storeId,
        String sourceType,
        String appointmentType,
        LocalDateTime startAt,
        LocalDateTime endAt,
        int personCount,
        long workstationId,
        String note,
        String idempotencyKey,
        long operatorId) {
}
