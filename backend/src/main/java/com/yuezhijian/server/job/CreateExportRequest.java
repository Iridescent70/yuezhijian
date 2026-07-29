package com.yuezhijian.server.job;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateExportRequest(
        @NotBlank(message = "导出类型不能为空")
        @Pattern(regexp = "SERVICE_FEEDBACK|MEMBER|SERVICE_CATALOG|PRODUCT_CATALOG", message = "导出类型无效")
        String exportType,
        @Size(max = 100, message = "查询关键词不能超过100个字符") String keyword,
        String status,
        Boolean overdue) {
}
