package com.yuezhijian.server.asset;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("memory")
public class MemoryCardRepository implements CardRepository {
    private final Map<Long, CardTypeDetail> cardTypes = new LinkedHashMap<>();
    private final Map<Long, MemberCardDetail> cards = new LinkedHashMap<>();
    private final Map<String, CardSaleResult> sales = new LinkedHashMap<>();
    private final AtomicLong typeIds = new AtomicLong(501);
    private final AtomicLong orderIds = new AtomicLong(600);
    private final AtomicLong cardIds = new AtomicLong(700);
    private final AtomicLong balanceIds = new AtomicLong(800);
    private final AtomicLong ledgerIds = new AtomicLong(900);
    private final AssetNumberGenerator numbers;

    public MemoryCardRepository(AssetNumberGenerator numbers) {
        this.numbers = numbers;
        cardTypes.put(501L, new CardTypeDetail(
                501L, "BASIC_NAIL_10", "基础单色美甲10次卡", new BigDecimal("1280.0000"),
                new BigDecimal("1680.0000"), new BigDecimal("10.0000"), 365, BigDecimal.ZERO.setScale(4),
                "限基础单色美甲项目使用。", 30, List.of(2L),
                List.of(new CardServiceRule(
                        301L, "SVC001", "基础单色美甲", new BigDecimal("10.0000"), BigDecimal.ONE, 10)),
                "ACTIVE", "1"));
    }

    @Override
    public synchronized List<CardTypeDetail> searchCardTypes(Long storeId, String keyword, String status) {
        String normalized = keyword == null ? null : keyword.toLowerCase(Locale.ROOT);
        return cardTypes.values().stream()
                .filter(item -> storeId == null || item.storeIds().contains(storeId))
                .filter(item -> normalized == null || item.code().toLowerCase(Locale.ROOT).contains(normalized)
                        || item.name().toLowerCase(Locale.ROOT).contains(normalized))
                .filter(item -> status == null || status.equals(item.status()))
                .sorted(Comparator.comparingLong(CardTypeDetail::id).reversed())
                .toList();
    }

    @Override
    public synchronized Optional<CardTypeDetail> findCardType(long id) {
        return Optional.ofNullable(cardTypes.get(id));
    }

    @Override
    public synchronized boolean existsCardTypeCode(String code) {
        return cardTypes.values().stream().anyMatch(item -> item.code().equalsIgnoreCase(code));
    }

    @Override
    public synchronized CardTypeDetail createCardType(CardTypeDraft draft) {
        long id = typeIds.incrementAndGet();
        CardTypeDetail result = new CardTypeDetail(
                id, draft.code(), draft.name(), draft.salePrice(), draft.listPrice(), draft.totalTimes(),
                draft.validDays(), draft.purchaseThreshold(), draft.instructions(), draft.autoRemindDays(),
                List.copyOf(draft.storeIds()), List.copyOf(draft.serviceRules()), "ACTIVE", "1");
        cardTypes.put(id, result);
        return result;
    }

    @Override
    public synchronized List<MemberCardSummary> memberCards(long memberId, String status) {
        return cards.values().stream().map(MemberCardDetail::card)
                .filter(item -> item.memberId() == memberId)
                .filter(item -> status == null || status.equals(item.status()))
                .sorted(Comparator.comparing(MemberCardSummary::expiresAt).thenComparing(MemberCardSummary::id))
                .toList();
    }

    @Override
    public synchronized Optional<MemberCardDetail> findMemberCard(long id) {
        return Optional.ofNullable(cards.get(id));
    }

    @Override
    public synchronized Optional<CardSaleResult> findSaleByIdempotencyKey(String key) {
        return Optional.ofNullable(sales.get(key));
    }

    @Override
    public synchronized CardSaleResult purchase(PurchaseMemberCardDraft draft) {
        Optional<CardSaleResult> existing = findSaleByIdempotencyKey(draft.idempotencyKey());
        if (existing.isPresent()) return existing.get();
        long orderId = orderIds.incrementAndGet();
        List<MemberCardSummary> purchased = new ArrayList<>();
        for (int itemIndex = 0; itemIndex < draft.quantity(); itemIndex++) {
            long cardId = cardIds.incrementAndGet();
            LocalDateTime expiresAt = draft.startedAt().plusDays(draft.cardType().validDays());
            BigDecimal total = draft.cardType().serviceRules().stream().map(CardServiceRule::includedTimes)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            MemberCardSummary summary = new MemberCardSummary(
                    cardId, numbers.memberCardNo(), draft.memberId(), draft.cardType().id(),
                    draft.cardType().code(), draft.cardType().name(), draft.storeId(), draft.storeName(),
                    draft.cardType().salePrice(), total, total, BigDecimal.ZERO.setScale(4),
                    draft.startedAt(), expiresAt, "ACTIVE", "1");
            List<MemberCardBalanceItem> balances = new ArrayList<>();
            List<MemberCardLedgerItem> ledgers = new ArrayList<>();
            for (int ruleIndex = 0; ruleIndex < draft.cardType().serviceRules().size(); ruleIndex++) {
                CardServiceRule rule = draft.cardType().serviceRules().get(ruleIndex);
                balances.add(new MemberCardBalanceItem(
                        balanceIds.incrementAndGet(), rule.serviceId(), rule.serviceCode(), rule.serviceName(),
                        rule.includedTimes(), rule.includedTimes(), BigDecimal.ZERO.setScale(4),
                        rule.deductTimes(), "1"));
                ledgers.add(new MemberCardLedgerItem(
                        ledgerIds.incrementAndGet(), numbers.cardLedgerNo(), rule.serviceId(), rule.serviceName(),
                        "PURCHASE", BigDecimal.ZERO.setScale(4), rule.includedTimes(), rule.includedTimes(),
                        allocatedValue(draft.cardType().salePrice(), rule.includedTimes(), total),
                        "CARD_SALE", orderId, LocalDateTime.now(),
                        "card-sale:" + orderId + ':' + cardId + ':' + ruleIndex, null, "售卡入账"));
            }
            cards.put(cardId, new MemberCardDetail(summary, List.copyOf(balances), List.copyOf(ledgers)));
            purchased.add(summary);
        }
        CardSaleResult result = new CardSaleResult(
                orderId, draft.orderNo(), draft.cardType().salePrice().multiply(BigDecimal.valueOf(draft.quantity())),
                List.copyOf(purchased));
        sales.put(draft.idempotencyKey(), result);
        return result;
    }

    private BigDecimal allocatedValue(BigDecimal price, BigDecimal included, BigDecimal total) {
        return price.multiply(included).divide(total, 4, java.math.RoundingMode.HALF_UP);
    }
}
