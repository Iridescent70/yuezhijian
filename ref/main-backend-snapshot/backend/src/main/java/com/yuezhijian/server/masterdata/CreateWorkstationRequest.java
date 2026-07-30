package com.yuezhijian.server.masterdata;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateWorkstationRequest(
        @NotNull Long storeId,
        @NotBlank @Size(max = 64) String code,
        @NotBlank @Size(max = 100) String name,
        @Min(1) @Max(100) int capacity,
        int sortNo) {
}
