package com.yuezhijian.server.inventory;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

public record CountLineInput(
        @Positive long lineId,
        @DecimalMin("0") @Digits(integer = 15, fraction = 4) BigDecimal actualQuantity) {
}
