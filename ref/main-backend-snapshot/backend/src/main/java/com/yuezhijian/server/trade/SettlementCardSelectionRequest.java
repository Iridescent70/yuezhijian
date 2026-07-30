package com.yuezhijian.server.trade;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record SettlementCardSelectionRequest(
        @NotNull @Positive Long billLineId,
        @NotNull @Positive Long memberCardId) {}
