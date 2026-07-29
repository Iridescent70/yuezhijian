package com.yuezhijian.server.visit;

import java.time.LocalDate;

public record VisitTaskQuery(
        Long storeId,
        Long employeeId,
        String status,
        LocalDate dueDate,
        String keyword) {
}
