package com.yuezhijian.server.masterdata;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record UpdateServiceItemRequest(
        @NotBlank @Size(max = 200) String name,
        @NotNull Long categoryId,
        @Min(5) @Max(1440) int durationMinutes,
        @NotNull @DecimalMin("0") BigDecimal costAmount,
        @NotNull @DecimalMin("0") BigDecimal listPrice,
        @NotNull Long storeId,
        @NotNull @DecimalMin("0") BigDecimal storePrice,
        @NotBlank String saleStatus,
        @NotBlank String status,
        @Size(max = 2000) String description,
        @NotBlank String version) {
}
