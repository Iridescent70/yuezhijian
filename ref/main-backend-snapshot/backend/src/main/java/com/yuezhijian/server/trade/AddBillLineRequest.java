package com.yuezhijian.server.trade;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record AddBillLineRequest(
        @NotNull Long serviceId,
        @NotNull @DecimalMin("0.0001") BigDecimal quantity,
        Long employeeId,
        @Size(max = 500) String note,
        @NotNull String version) {
}
