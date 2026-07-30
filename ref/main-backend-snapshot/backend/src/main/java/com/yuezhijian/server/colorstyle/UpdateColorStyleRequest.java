package com.yuezhijian.server.colorstyle;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

public record UpdateColorStyleRequest(
        @NotBlank @Size(max = 100) String name,
        @Size(max = 500) String description,
        @Min(0) @Max(9999) int sortNo,
        @NotBlank String status,
        @NotEmpty @Size(max = 20) List<Long> categoryIds,
        @NotBlank String version) {
}
