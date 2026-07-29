package com.yuezhijian.server.asset;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Profile("sqlserver")
public class SqlServerCardRepository implements CardRepository {
    private final CardMapper mapper;
    private final AssetNumberGenerator numbers;

    public SqlServerCardRepository(CardMapper mapper, AssetNumberGenerator numbers) {
        this.mapper = mapper;
        this.numbers = numbers;
    }

    @Override
    public List<CardTypeDetail> searchCardTypes(Long storeId, String keyword, String status) {
        return mapper.searchCardTypes(storeId, keyword, status).stream().map(this::toCardType).toList();
    }

    @Override
    public Optional<CardTypeDetail> findCardType(long id) {
        return Optional.ofNullable(mapper.findCardType(id)).map(this::toCardType);
    }

    @Override
    public boolean existsCardTypeCode(String code) {
        return mapper.countCardTypeCode(code) > 0;
    }

    @Override
    @Transactional
    public CardTypeDetail createCardType(CardTypeDraft draft) {
        long id = mapper.insertCardType(draft);
        for (int index = 0; index < draft.storeIds().size(); index++) {
            mapper.insertCardTypeStore(id, draft.storeIds().get(index), (index + 1) * 10);
        }
        for (CardServiceRule rule : draft.serviceRules()) mapper.insertCardServiceRule(id, rule);
        return requireCardType(id);
    }

    @Override
    public List<MemberCardSummary> memberCards(long memberId, String status) {
        return mapper.findMemberCards(memberId, status).stream().map(this::toMemberCard).toList();
    }

    @Override
    public Optional<MemberCardDetail> findMemberCard(long id) {
        MemberCardRow row = mapper.findMemberCard(id);
        if (row == null) return Optional.empty();
        return Optional.of(new MemberCardDetail(
                toMemberCard(row),
                mapper.findMemberCardBalances(id).stream().map(this::toBalance).toList(),
                mapper.findMemberCardLedgers(id)));
    }

    @Override
    public Optional<CardSaleResult> findSaleByIdempotencyKey(String key) {
        CardSaleRow order = mapper.findSaleByIdempotencyKey(key);
        if (order == null) return Optional.empty();
        return Optional.of(new CardSaleResult(
                order.id(), order.orderNo(), order.totalAmount(),
                mapper.findMemberCardsByOrder(order.id()).stream().map(this::toMemberCard).toList()));
    }

    @Override
    @Transactional
    public CardSaleResult purchase(PurchaseMemberCardDraft draft) {
        Optional<CardSaleResult> existing = findSaleByIdempotencyKey(draft.idempotencyKey());
        if (existing.isPresent()) return existing.get();
        long orderId = mapper.insertSaleOrder(draft);
        BigDecimal totalTimes = draft.cardType().serviceRules().stream().map(CardServiceRule::includedTimes)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        for (int cardIndex = 0; cardIndex < draft.quantity(); cardIndex++) {
            long cardId = mapper.insertMemberCard(
                    orderId, numbers.memberCardNo(), draft, draft.startedAt().plusDays(draft.cardType().validDays()));
            for (int ruleIndex = 0; ruleIndex < draft.cardType().serviceRules().size(); ruleIndex++) {
                CardServiceRule rule = draft.cardType().serviceRules().get(ruleIndex);
                mapper.insertMemberCardBalance(cardId, rule);
                mapper.insertPurchaseLedger(
                        numbers.cardLedgerNo(), cardId, rule,
                        allocatedValue(draft.cardType().salePrice(), rule.includedTimes(), totalTimes),
                        orderId, "card-sale:" + orderId + ':' + cardId + ':' + ruleIndex, draft.operatorId());
            }
        }
        return findSaleByIdempotencyKey(draft.idempotencyKey()).orElseThrow();
    }

    private CardTypeDetail toCardType(CardTypeRow row) {
        return new CardTypeDetail(
                row.id(), row.code(), row.name(), row.salePrice(), row.listPrice(), row.totalTimes(),
                row.validDays(), row.purchaseThreshold(), row.instructions(), row.autoRemindDays(),
                mapper.findCardTypeStores(row.id()), mapper.findCardServiceRules(row.id()),
                row.status(), encode(row.rowVersion()));
    }

    private MemberCardSummary toMemberCard(MemberCardRow row) {
        return new MemberCardSummary(
                row.id(), row.cardNo(), row.memberId(), row.cardTypeId(), row.cardTypeCode(), row.cardTypeName(),
                row.purchaseStoreId(), row.purchaseStoreName(), row.purchasePrice(), row.totalTimes(),
                row.remainingTimes(), row.frozenTimes(), row.startedAt(), row.expiresAt(), row.status(),
                encode(row.rowVersion()));
    }

    private MemberCardBalanceItem toBalance(MemberCardBalanceRow row) {
        return new MemberCardBalanceItem(
                row.id(), row.serviceId(), row.serviceCode(), row.serviceName(), row.totalTimes(),
                row.remainingTimes(), row.frozenTimes(), row.deductTimes(), encode(row.rowVersion()));
    }

    private CardTypeDetail requireCardType(long id) {
        return findCardType(id).orElseThrow(() -> new IllegalArgumentException("次卡类型不存在"));
    }

    private BigDecimal allocatedValue(BigDecimal price, BigDecimal included, BigDecimal total) {
        return price.multiply(included).divide(total, 4, RoundingMode.HALF_UP);
    }

    private String encode(byte[] value) { return Base64.getEncoder().encodeToString(value); }
}
