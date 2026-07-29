package com.yuezhijian.server.visit;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

public record CreateVisitRecordRequest(
        @NotNull @Positive Long employeeId,
        @NotBlank String resultCode,
        @Min(1) @Max(5) Integer satisfactionScore,
        boolean complaintFlag,
        @Size(max = 2000) String content,
        LocalDateTime nextFollowAt) {
}
