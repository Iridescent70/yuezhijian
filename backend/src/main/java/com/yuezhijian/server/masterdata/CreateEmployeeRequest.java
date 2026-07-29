package com.yuezhijian.server.masterdata;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateEmployeeRequest(
        @NotBlank @Size(max = 64) String employeeNo,
        @NotBlank @Size(max = 100) String name,
        String mobile,
        @NotNull Long positionId,
        @NotNull Long primaryStoreId,
        boolean canService,
        boolean canSell) {
}
