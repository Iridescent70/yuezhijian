package com.yuezhijian.server.masterdata;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record UpdateEmployeeRequest(
        @NotBlank @Size(max = 100) String name,
        String mobile,
        @NotNull Long positionId,
        @NotNull Long primaryStoreId,
        LocalDate hireDate,
        LocalDate leaveDate,
        boolean canService,
        boolean canSell,
        @NotBlank String status,
        @NotBlank String version) {
}
