package com.yuezhijian.server.member;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record CreateMemberRequest(
        @NotBlank(message = "请输入会员姓名") @Size(max = 100) String fullName,
        @Size(max = 100) String nickname,
        @NotBlank(message = "请输入手机号") String mobile,
        String gender,
        LocalDate birthday,
        @Email(message = "邮箱格式不正确") @Size(max = 255) String email,
        String sourceType,
        @NotNull(message = "请选择入会门店") Long joinStoreId,
        Long ownerStoreId,
        Long advisorEmployeeId,
        @Size(max = 64) String membershipCardNo) {
}
