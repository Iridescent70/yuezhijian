package com.yuezhijian.server.appointment;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AppointmentTransitionRequest(
        @NotBlank String version,
        @Size(max = 64) String reasonCode,
        @Size(max = 500) String note,
        @Min(1) @Max(100) Integer personCount) {
}
