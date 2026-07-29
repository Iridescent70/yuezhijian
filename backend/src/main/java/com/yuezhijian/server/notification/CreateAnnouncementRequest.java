package com.yuezhijian.server.notification;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.List;

public record CreateAnnouncementRequest(
        @NotBlank @Size(max = 100) String title,
        @NotBlank @Size(max = 4000) String body,
        @NotBlank String scopeType,
        @NotNull @Size(max = 100) List<@Positive Long> storeIds,
        LocalDateTime validFrom,
        LocalDateTime validTo,
        @Min(0) @Max(9999) int priority,
        boolean pinned,
        @NotBlank String status) {
}
