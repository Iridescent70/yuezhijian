package com.yuezhijian.server.colorstyle;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateColorStyleCategoryRequest(
        Long parentId,
        @NotBlank @Size(max = 64) @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9_-]*") String code,
        @NotBlank @Size(max = 100) String name,
        @Min(0) @Max(9999) int sortNo) {
}
