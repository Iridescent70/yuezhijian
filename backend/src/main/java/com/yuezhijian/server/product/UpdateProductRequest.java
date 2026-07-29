package com.yuezhijian.server.product;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record UpdateProductRequest(
        @NotBlank @Size(max = 200) String name,
        @NotNull Long categoryId,
        @NotNull Long unitId,
        @Size(max = 64) String barcode,
        @NotNull @DecimalMin("0") BigDecimal costPrice,
        @NotNull @DecimalMin("0") BigDecimal salePrice,
        boolean trackStock,
        @Size(max = 1000) String description,
        @NotBlank String status,
        @NotNull Long storeId,
        @NotNull @DecimalMin("0") BigDecimal storePrice,
        @NotBlank String saleStatus,
        @NotBlank String version) {
}
