package com.yuezhijian.server.appointment;

import java.time.LocalDateTime;

public record AppointmentSummary(
        long id,
        String appointmentNo,
        Long memberId,
        String customerName,
        String maskedMobile,
        long storeId,
        String storeName,
        String sourceType,
        String appointmentType,
        LocalDateTime startAt,
        LocalDateTime endAt,
        int personCount,
        Long employeeId,
        String employeeName,
        Long workstationId,
        String workstationName,
        String serviceNames,
        String note,
        String status,
        String version) {
}
