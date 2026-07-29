package com.yuezhijian.server.settings;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateSystemParameterRequest(
        @NotBlank @Size(max = 2000) String value,
        @NotBlank @Pattern(regexp = "ACTIVE|DISABLED") String status,
        @NotBlank String version) {
}
