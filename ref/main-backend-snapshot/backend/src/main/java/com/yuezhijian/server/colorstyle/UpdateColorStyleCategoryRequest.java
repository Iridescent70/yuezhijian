package com.yuezhijian.server.colorstyle;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateColorStyleCategoryRequest(
        Long parentId,
        @NotBlank @Size(max = 100) String name,
        @Min(0) @Max(9999) int sortNo,
        @NotBlank String status,
        @NotBlank String version) {
}
