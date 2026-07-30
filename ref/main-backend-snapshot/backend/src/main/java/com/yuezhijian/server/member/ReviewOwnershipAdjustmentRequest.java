package com.yuezhijian.server.member;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ReviewOwnershipAdjustmentRequest(
        @Size(max = 500) String comment,
        @NotBlank(message = "缺少归属调整数据版本") String version) {
}
