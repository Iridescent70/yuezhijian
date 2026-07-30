package com.yuezhijian.server.settings;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;

public record UpdateSatisfactionRuleRequest(
        @NotBlank @Size(max = 100) String ruleName,
        @NotEmpty @Size(max = 20) List<@NotBlank @Size(max = 100) String> keywords,
        @Min(1) @Max(5) int score,
        @Size(max = 20) Map<@NotBlank @Size(max = 100) String, @NotBlank @Size(max = 200) String> componentMapping,
        @Min(0) @Max(9999) int priority,
        @NotBlank @Pattern(regexp = "ACTIVE|DISABLED") String status,
        @NotBlank String version) {
}
