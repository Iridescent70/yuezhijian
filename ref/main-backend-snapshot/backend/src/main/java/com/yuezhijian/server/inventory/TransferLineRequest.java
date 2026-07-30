package com.yuezhijian.server.inventory;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record TransferLineRequest(
        @Positive long giftId,
        @DecimalMin(value = "0", inclusive = false) @Digits(integer = 15, fraction = 4) BigDecimal quantity,
        @Size(max = 200) String note) {
}
