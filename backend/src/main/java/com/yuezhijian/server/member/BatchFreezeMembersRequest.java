package com.yuezhijian.server.member;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public record BatchFreezeMembersRequest(
        @NotNull(message = "请选择会员") @Size(min = 1, max = 100, message = "单次最多选择100位会员")
        List<@NotNull @Positive Long> memberIds,
        @NotBlank(message = "请填写冻结原因") @Size(max = 500) String reason) {
    public BatchFreezeMembersRequest {
        memberIds = memberIds == null ? null : Collections.unmodifiableList(new ArrayList<>(memberIds));
    }
}
