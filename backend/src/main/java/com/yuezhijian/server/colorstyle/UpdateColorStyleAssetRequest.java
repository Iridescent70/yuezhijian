package com.yuezhijian.server.colorstyle;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record UpdateColorStyleAssetRequest(
        @Min(0) @Max(9999) int sortNo,
        @NotBlank String status,
        @NotBlank String version) {
}
