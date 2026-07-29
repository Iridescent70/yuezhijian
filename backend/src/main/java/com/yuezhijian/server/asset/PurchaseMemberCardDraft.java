package com.yuezhijian.server.asset;

import java.time.LocalDateTime;

public record PurchaseMemberCardDraft(
        String orderNo,
        long memberId,
        CardTypeDetail cardType,
        int quantity,
        long storeId,
        String storeName,
        Long salesEmployeeId,
        long paymentMethodId,
        String paymentMethodName,
        String externalReference,
        LocalDateTime startedAt,
        String idempotencyKey,
        long operatorId) {}
