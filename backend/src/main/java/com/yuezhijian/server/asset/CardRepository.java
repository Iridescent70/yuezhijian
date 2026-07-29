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
    Optional<Long> saleEmployeeId(long memberCardId);
    Optional<CardSaleResult> findSaleByIdempotencyKey(String idempotencyKey);
    CardSaleResult purchase(PurchaseMemberCardDraft draft);
    void consumeCard(CardSettlementConsumption command);
    void refundCard(CardRefundCommand command);
    List<CardConsumptionRepriceItem> consumptionRepriceItems(long memberCardId);

    Optional<CardExchangeQuote> findExchangeQuote(String quoteNo);

    CardExchangeQuote createExchangeQuote(CardExchangeQuoteDraft draft);

    Optional<CardExchangeResult> findExchangeByIdempotencyKey(String key);

    CardExchangeResult exchange(CardExchangeCommand command);

    Optional<CardTransferResult> findTransferByIdempotencyKey(String key);

    CardTransferResult transfer(CardTransferCommand command);

    CardRefundQuote createRefundQuote(CardRefundQuoteDraft draft);
    Optional<CardRefundQuote> findRefundQuote(String quoteNo);
    List<CardRefundRequestSummary> refundRequests(String status);
    Optional<CardRefundRequestDetail> findRefundRequest(long id);
    Optional<CardRefundRequestDetail> findRefundRequestByRequestKey(String key);
    Optional<CardRefundRequestDetail> findRefundRequestByExecutionKey(String key);
    Optional<CardRefundRequestDetail> findActiveRefundRequest(long memberCardId);
    CardRefundRequestDetail submitRefundRequest(CardRefundSubmission submission);
    CardRefundRequestDetail reviewRefundRequest(CardRefundReviewCommand command);
    CardRefundRequestDetail executeRefund(CardRefundExecutionCommand command);
    CardRefundRequestDetail updateRefundCommissionStatus(long requestId, String status, long operatorId);
}
