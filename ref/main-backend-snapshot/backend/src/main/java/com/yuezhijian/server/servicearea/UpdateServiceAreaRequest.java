package com.yuezhijian.server.servicearea;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record UpdateServiceAreaRequest(
        @NotBlank @Size(max = 64) String city,
        @NotBlank @Size(max = 64) String district,
        @NotBlank @Size(max = 300) String address,
        @NotNull @DecimalMin("-180") @DecimalMax("180") @Digits(integer = 3, fraction = 7)
        BigDecimal longitude,
        @NotNull @DecimalMin("-90") @DecimalMax("90") @Digits(integer = 2, fraction = 7)
        BigDecimal latitude,
        @NotNull @DecimalMin("0.001") @DecimalMax("200.000") @Digits(integer = 3, fraction = 3)
        BigDecimal radiusKm,
        @NotNull @DecimalMin("0") @DecimalMax("999999999999999.9999") @Digits(integer = 15, fraction = 4)
        BigDecimal visitFee,
        @NotBlank String status,
        @NotBlank String version) {
}
