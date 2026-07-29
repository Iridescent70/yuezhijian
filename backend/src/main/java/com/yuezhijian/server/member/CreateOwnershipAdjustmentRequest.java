package com.yuezhijian.server.member;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.Map;

public record CreateOwnershipAdjustmentRequest(
        @NotNull(message = "请选择新归属门店") Long newStoreId,
        @NotNull(message = "请选择生效日期") LocalDate effectiveDate,
        @NotNull(message = "缺少分润规则快照") Map<String, Object> shareRule,
        @NotBlank(message = "请填写归属调整原因") @Size(max = 500) String reason,
        @NotBlank(message = "缺少会员数据版本") String memberVersion) {
}
