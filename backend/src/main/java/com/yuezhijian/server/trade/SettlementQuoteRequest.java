package com.yuezhijian.server.trade;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import java.math.BigDecimal;
import java.util.List;

public record SettlementQuoteRequest(
        List<@Valid SettlementPaymentRequest> payments,
        @DecimalMin("0.00") BigDecimal balanceAmount,
        @Min(0) int points,
        List<@Valid SettlementCardSelectionRequest> cards) {
    public SettlementQuoteRequest {
        payments = payments == null ? List.of() : List.copyOf(payments);
        balanceAmount = balanceAmount == null ? BigDecimal.ZERO : balanceAmount;
        cards = cards == null ? List.of() : List.copyOf(cards);
    }
}
