package com.yuezhijian.server.appointment;

import java.time.LocalDateTime;
import java.util.List;

public record AppointmentUpdate(
        long id,
        LocalDateTime startAt,
        LocalDateTime endAt,
        int personCount,
        long employeeId,
        long workstationId,
        List<AppointmentServiceLine> services,
        boolean designated,
        String note,
        String version,
        long operatorId) {
    public AppointmentUpdate {
        services = List.copyOf(services);
    }
}
