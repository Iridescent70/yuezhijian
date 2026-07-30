package com.yuezhijian.server.masterdata;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateUnitRequest(
        @NotBlank @Size(max = 32) String code,
        @NotBlank @Size(max = 50) String name,
        @Min(0) @Max(4) int decimalPlaces) {
}
