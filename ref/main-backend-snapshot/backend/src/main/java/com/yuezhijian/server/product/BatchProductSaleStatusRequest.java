package com.yuezhijian.server.product;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public record BatchProductSaleStatusRequest(
        @NotNull(message = "请选择产品") @Size(min = 1, max = 100, message = "单次最多选择100个产品")
        List<@NotNull @Positive Long> productIds,
        @NotBlank(message = "销售状态不能为空")
        @Pattern(regexp = "ON_SALE|OFF_SALE", message = "销售状态无效") String saleStatus) {
    public BatchProductSaleStatusRequest {
        productIds = productIds == null ? null : Collections.unmodifiableList(new ArrayList<>(productIds));
    }
}
