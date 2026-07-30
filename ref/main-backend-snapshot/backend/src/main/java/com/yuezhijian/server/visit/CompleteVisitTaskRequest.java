package com.yuezhijian.server.visit;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CompleteVisitTaskRequest(
        @NotBlank @Size(max = 1000) String conclusion) {
}
