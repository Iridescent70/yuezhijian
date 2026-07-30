package com.yuezhijian.server.member;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangeMemberStatusRequest(
        @NotBlank(message = "请选择会员状态") String status,
        @NotBlank(message = "请填写状态变更原因") @Size(max = 500) String reason,
        @NotBlank(message = "缺少会员数据版本") String version) {
}
