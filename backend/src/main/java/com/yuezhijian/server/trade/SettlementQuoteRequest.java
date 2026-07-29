package com.yuezhijian.server.trade;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record SettlementQuoteRequest(@NotEmpty List<@Valid SettlementPaymentRequest> payments) {
    public SettlementQuoteRequest {
        payments = payments == null ? List.of() : List.copyOf(payments);
    }
}
