package com.yuezhijian.server.notification;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record CreateNotificationTemplateRequest(
        @NotBlank @Size(max = 64) String eventCode,
        @NotBlank @Size(max = 100) String eventName,
        @NotBlank @Size(max = 100) String titleTemplate,
        @NotBlank @Size(max = 4000) String bodyTemplate,
        @NotNull @Size(max = 20) List<@NotBlank @Size(max = 64) String> variables,
        @NotBlank String status) {
}
