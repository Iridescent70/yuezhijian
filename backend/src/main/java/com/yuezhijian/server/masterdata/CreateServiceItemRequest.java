package com.yuezhijian.server.masterdata;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;

public record CreateServiceItemRequest(
        @NotBlank @Size(max = 64) String code,
        @NotBlank @Size(max = 200) String name,
        @NotNull Long categoryId,
        @Min(5) @Max(1440) int durationMinutes,
        @NotNull @DecimalMin("0") BigDecimal costAmount,
        @NotNull @DecimalMin("0") BigDecimal listPrice,
        @NotNull @DecimalMin("0") BigDecimal storePrice,
        @NotEmpty List<Long> storeIds,
        @Size(max = 2000) String description) {
    public CreateServiceItemRequest {
        storeIds = storeIds == null ? List.of() : List.copyOf(storeIds);
    }
}
