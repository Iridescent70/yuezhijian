package com.yuezhijian.server.appointment;

import java.util.List;

public record AppointmentDetail(
        AppointmentSummary appointment,
        List<AppointmentServiceLine> services,
        List<AppointmentHistoryItem> history) {
    public AppointmentDetail {
        services = List.copyOf(services);
        history = List.copyOf(history);
    }
}
