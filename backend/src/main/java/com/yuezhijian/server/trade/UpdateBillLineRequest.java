package com.yuezhijian.server.trade;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record UpdateBillLineRequest(
        @NotNull @DecimalMin("0.0001") BigDecimal quantity,
        Long employeeId,
        @Size(max = 500) String note,
        @NotBlank String version) {}
