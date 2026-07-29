package com.yuezhijian.server.appointment;

import java.time.LocalDateTime;

public record AvailabilitySlot(
        LocalDateTime startAt,
        LocalDateTime endAt,
        boolean available,
        String unavailableReason) {
}
