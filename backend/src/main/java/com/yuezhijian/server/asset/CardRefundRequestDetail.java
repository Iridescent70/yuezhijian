package com.yuezhijian.server.asset;

import java.util.List;

public record CardRefundRequestDetail(
        CardRefundRequestSummary request,
        List<CardConsumptionRepriceItem> consumedItems,
        CardRefundPayment payment) {
    public CardRefundRequestDetail { consumedItems = List.copyOf(consumedItems); }
}
