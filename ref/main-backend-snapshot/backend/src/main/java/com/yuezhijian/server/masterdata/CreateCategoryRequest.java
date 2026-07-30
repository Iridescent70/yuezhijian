package com.yuezhijian.server.masterdata;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateCategoryRequest(
        @NotBlank String type,
        @NotBlank @Size(max = 64) String code,
        @NotBlank @Size(max = 100) String name,
        @Min(0) @Max(9999) int sortNo) {
}
