package com.yuezhijian.server.appointment;

import java.math.BigDecimal;

public record AppointmentServiceLine(
        long serviceId,
        String serviceName,
        int durationMinutes,
        BigDecimal price,
        int sortNo) {
}
