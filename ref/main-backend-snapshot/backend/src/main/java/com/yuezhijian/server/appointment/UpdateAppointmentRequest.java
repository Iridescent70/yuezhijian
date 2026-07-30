package com.yuezhijian.server.appointment;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.List;

public record UpdateAppointmentRequest(
        @NotNull LocalDateTime startAt,
        @Min(1) @Max(100) int personCount,
        @NotNull Long employeeId,
        @NotNull Long workstationId,
        @NotEmpty List<Long> serviceIds,
        boolean designated,
        @Size(max = 1000) String note,
        @NotBlank String version) {
    public UpdateAppointmentRequest {
        serviceIds = serviceIds == null ? List.of() : List.copyOf(serviceIds);
    }
}
