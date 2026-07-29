package com.yuezhijian.server.asset;

import java.util.List;
import java.util.Optional;

public interface CardRepository {
    List<CardTypeDetail> searchCardTypes(Long storeId, String keyword, String status);
    Optional<CardTypeDetail> findCardType(long id);
    boolean existsCardTypeCode(String code);
    CardTypeDetail createCardType(CardTypeDraft draft);
    List<MemberCardSummary> memberCards(long memberId, String status);
    Optional<MemberCardDetail> findMemberCard(long id);
    Optional<CardSaleResult> findSaleByIdempotencyKey(String idempotencyKey);
    CardSaleResult purchase(PurchaseMemberCardDraft draft);
    void consumeCard(CardSettlementConsumption command);
    void refundCard(CardRefundCommand command);

    Optional<CardExchangeQuote> findExchangeQuote(String quoteNo);

    CardExchangeQuote createExchangeQuote(CardExchangeQuoteDraft draft);

    Optional<CardExchangeResult> findExchangeByIdempotencyKey(String key);

    CardExchangeResult exchange(CardExchangeCommand command);

    Optional<CardTransferResult> findTransferByIdempotencyKey(String key);

    CardTransferResult transfer(CardTransferCommand command);
}
