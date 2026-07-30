package com.yuezhijian.server.member;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;

public record UpdateMemberTagsRequest(
        @NotNull(message = "缺少待添加标签") List<@Positive Long> addIds,
        @NotNull(message = "缺少待移除标签") List<@Positive Long> removeIds,
        @NotBlank(message = "缺少会员数据版本") String version) {
    public UpdateMemberTagsRequest {
        addIds = addIds == null ? null : List.copyOf(addIds);
        removeIds = removeIds == null ? null : List.copyOf(removeIds);
    }
}
