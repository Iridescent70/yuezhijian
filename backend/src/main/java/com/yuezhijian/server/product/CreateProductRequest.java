package com.yuezhijian.server.product;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;

public record CreateProductRequest(
        @NotBlank @Size(max = 64) String code,
        @NotBlank @Size(max = 200) String name,
        @NotNull Long categoryId,
        @NotNull Long unitId,
        @Size(max = 64) String barcode,
        @NotNull @DecimalMin("0") BigDecimal costPrice,
        @NotNull @DecimalMin("0") BigDecimal salePrice,
        @NotNull @DecimalMin("0") BigDecimal storePrice,
        boolean trackStock,
        @NotEmpty List<Long> storeIds,
        @Size(max = 1000) String description) {
    public CreateProductRequest {
        storeIds = storeIds == null ? List.of() : List.copyOf(storeIds);
    }
}
