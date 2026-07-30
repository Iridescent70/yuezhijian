package com.yuezhijian.server.cancelreason;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateCancelReasonRequest(
        @NotBlank @Size(max = 200) String name,
        boolean requiresNote,
        @Min(0) @Max(9999) int sortNo,
        @NotBlank String status,
        @NotBlank String version) {
}
