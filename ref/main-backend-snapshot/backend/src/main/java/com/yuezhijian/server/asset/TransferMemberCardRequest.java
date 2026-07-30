package com.yuezhijian.server.asset;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

public record TransferMemberCardRequest(
        @Positive long recipientMemberId,
        @NotNull LocalDateTime expiresAt,
        @Positive long storeId,
        @Positive Long employeeId,
        @NotBlank @Size(max = 500) String reason,
        @NotBlank @Size(max = 128) String sourceCardVersion,
        @NotBlank @Size(max = 128) String idempotencyKey) {}
