package com.yuezhijian.server.job;

import jakarta.validation.constraints.NotBlank;

public record CreateExportRequest(
        @NotBlank(message = "导出类型不能为空") String exportType,
        String status,
        Boolean overdue) {
}
