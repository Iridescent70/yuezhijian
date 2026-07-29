package com.yuezhijian.server.settings;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TestSatisfactionRuleRequest(@NotBlank @Size(max = 1000) String text) {
}
