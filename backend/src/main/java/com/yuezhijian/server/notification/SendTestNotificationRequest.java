package com.yuezhijian.server.notification;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.Map;

public record SendTestNotificationRequest(
        @Positive long templateId,
        @NotNull @Size(max = 20) Map<String, @Size(max = 200) String> variables) {
}
