package com.yuezhijian.server.feedback;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record HandleFeedbackRequest(
        @NotBlank String action,
        @Positive Long handlerId,
        @Size(max = 2000) String content,
        @Size(max = 2000) String result) {
}
