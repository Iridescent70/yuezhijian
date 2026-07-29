package com.yuezhijian.server.asset;

import jakarta.validation.constraints.Positive;

public record CardExchangeQuoteRequest(@Positive long targetCardTypeId) {}
