package com.yuezhijian.server.appointment;

import java.time.LocalDate;

public record AppointmentQuery(long storeId, LocalDate startDate, LocalDate endDate, String status) {
}
