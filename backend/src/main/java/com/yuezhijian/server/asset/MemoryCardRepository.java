package com.yuezhijian.server.asset;

import com.yuezhijian.server.common.DuplicateResourceException;
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
    private final Map<String, CardExchangeQuote> exchangeQuotes = new LinkedHashMap<>();
    private final Map<String, CardExchangeResult> exchanges = new LinkedHashMap<>();
    private final AtomicLong typeIds = new AtomicLong(501);
    private final AtomicLong orderIds = new AtomicLong(600);
    private final AtomicLong cardIds = new AtomicLong(700);
    private final AtomicLong balanceIds = new AtomicLong(800);
    private final AtomicLong ledgerIds = new AtomicLong(900);
    private final AtomicLong exchangeQuoteIds = new AtomicLong(1000);
    private final AtomicLong exchangeIds = new AtomicLong(1100);
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
        MemberCardDetail detail = cards.get(id);
        if (detail == null) return Optional.empty();
        List<MemberCardLedgerItem> ledgers = detail.ledgers().stream()
                .sorted(Comparator.comparing(MemberCardLedgerItem::occurredAt).reversed()
                        .thenComparing(MemberCardLedgerItem::id, Comparator.reverseOrder()))
                .toList();
        return Optional.of(new MemberCardDetail(detail.card(), detail.balances(), ledgers));
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

    @Override
    public synchronized void consumeCard(CardSettlementConsumption command) {
        MemberCardDetail detail = cards.get(command.memberCardId());
        if (detail == null || detail.card().memberId() != command.memberId()) {
            throw new IllegalArgumentException("会员次卡不存在");
        }
        if (!"ACTIVE".equals(detail.card().status()) || detail.card().expiresAt().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("会员次卡已失效");
        }
        MemberCardBalanceItem current = detail.balances().stream()
                .filter(item -> item.id() == command.memberCardBalanceId() && item.serviceId() == command.serviceId())
                .findFirst().orElseThrow(() -> new IllegalArgumentException("次卡不支持当前项目"));
        if (!current.version().equals(command.balanceVersion())) {
            throw new DuplicateResourceException("次卡次数已发生变化，请重新试算");
        }
        if (current.remainingTimes().compareTo(command.times()) < 0) throw new IllegalArgumentException("次卡剩余次数不足");
        BigDecimal after = current.remainingTimes().subtract(command.times());
        MemberCardBalanceItem updated = new MemberCardBalanceItem(
                current.id(), current.serviceId(), current.serviceCode(), current.serviceName(), current.totalTimes(),
                after, current.frozenTimes(), current.deductTimes(), nextVersion(current.version()));
        List<MemberCardBalanceItem> balances = detail.balances().stream()
                .map(item -> item.id() == updated.id() ? updated : item).toList();
        List<MemberCardLedgerItem> ledgers = new ArrayList<>(detail.ledgers());
        ledgers.add(new MemberCardLedgerItem(
                ledgerIds.incrementAndGet(), numbers.cardLedgerNo(), command.serviceId(), current.serviceName(),
                "CONSUME", current.remainingTimes(), command.times().negate(), after, command.amount(),
                "BILL", command.billId(), LocalDateTime.now(),
                "bill:" + command.billId() + ":line:" + command.billLineId(), null, command.displayName()));
        BigDecimal remainingTotal = balances.stream().map(MemberCardBalanceItem::remainingTimes)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        MemberCardSummary old = detail.card();
        MemberCardSummary card = new MemberCardSummary(
                old.id(), old.cardNo(), old.memberId(), old.cardTypeId(), old.cardTypeCode(), old.cardTypeName(),
                old.purchaseStoreId(), old.purchaseStoreName(), old.purchasePrice(), old.totalTimes(),
                remainingTotal, old.frozenTimes(), old.startedAt(), old.expiresAt(),
                remainingTotal.signum() == 0 ? "EXHAUSTED" : old.status(), nextVersion(old.version()));
        cards.put(card.id(), new MemberCardDetail(card, balances, List.copyOf(ledgers)));
    }

    @Override
    public synchronized void refundCard(CardRefundCommand command) {
        MemberCardDetail detail = cards.get(command.memberCardId());
        if (detail == null || detail.card().memberId() != command.memberId()) {
            throw new IllegalArgumentException("会员次卡不存在");
        }
        MemberCardBalanceItem current = detail.balances().stream()
                .filter(item -> item.id() == command.memberCardBalanceId() && item.serviceId() == command.serviceId())
                .findFirst().orElseThrow(() -> new IllegalArgumentException("次卡项目余额不存在"));
        BigDecimal after = current.remainingTimes().add(command.times());
        if (after.add(current.frozenTimes()).compareTo(current.totalTimes()) > 0) {
            throw new IllegalArgumentException("次卡返还后次数超过原始次数");
        }
        MemberCardBalanceItem updated = new MemberCardBalanceItem(
                current.id(), current.serviceId(), current.serviceCode(), current.serviceName(), current.totalTimes(),
                after, current.frozenTimes(), current.deductTimes(), nextVersion(current.version()));
        List<MemberCardBalanceItem> balances = detail.balances().stream()
                .map(item -> item.id() == updated.id() ? updated : item).toList();
        List<MemberCardLedgerItem> ledgers = new ArrayList<>(detail.ledgers());
        ledgers.add(new MemberCardLedgerItem(
                ledgerIds.incrementAndGet(), numbers.cardLedgerNo(), command.serviceId(), current.serviceName(),
                "REFUND", current.remainingTimes(), command.times(), after, command.amount(),
                "REVERSAL", command.reversalId(), LocalDateTime.now(),
                "reversal:" + command.reversalId() + ":usage:" + command.usageId(),
                command.originalLedgerId(), command.note()));
        BigDecimal remainingTotal = balances.stream().map(MemberCardBalanceItem::remainingTimes)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        MemberCardSummary old = detail.card();
        String status = "EXHAUSTED".equals(old.status()) && old.expiresAt().isAfter(LocalDateTime.now())
                ? "ACTIVE" : old.status();
        MemberCardSummary card = new MemberCardSummary(
                old.id(), old.cardNo(), old.memberId(), old.cardTypeId(), old.cardTypeCode(), old.cardTypeName(),
                old.purchaseStoreId(), old.purchaseStoreName(), old.purchasePrice(), old.totalTimes(),
                remainingTotal, old.frozenTimes(), old.startedAt(), old.expiresAt(), status, nextVersion(old.version()));
        cards.put(card.id(), new MemberCardDetail(card, balances, List.copyOf(ledgers)));
    }

    @Override
    public synchronized Optional<CardExchangeQuote> findExchangeQuote(String quoteNo) {
        return Optional.ofNullable(exchangeQuotes.get(quoteNo));
    }

    @Override
    public synchronized CardExchangeQuote createExchangeQuote(CardExchangeQuoteDraft draft) {
        long id = exchangeQuoteIds.incrementAndGet();
        CardExchangeQuote result = new CardExchangeQuote(
                id, draft.quoteNo(), draft.oldCard().id(), draft.oldCard().cardNo(),
                draft.oldCard().cardTypeName(), draft.targetCardType().id(), draft.targetCardType().name(),
                draft.targetCardType().version(),
                draft.oldRemainingTimes(), draft.oldRemainingValue(), draft.targetCardType().salePrice(),
                draft.differenceAmount(), draft.oldCard().version(), draft.expiresAt(), false);
        exchangeQuotes.put(result.quoteNo(), result);
        return result;
    }

    @Override
    public synchronized Optional<CardExchangeResult> findExchangeByIdempotencyKey(String key) {
        return Optional.ofNullable(exchanges.get(key));
    }

    @Override
    public synchronized CardExchangeResult exchange(CardExchangeCommand command) {
        Optional<CardExchangeResult> existing = findExchangeByIdempotencyKey(command.idempotencyKey());
        if (existing.isPresent()) return existing.get();
        CardExchangeQuote quote = exchangeQuotes.get(command.quote().quoteNo());
        if (quote == null || quote.used() || quote.expiresAt().isBefore(LocalDateTime.now())) {
            throw new DuplicateResourceException("换卡试算已失效或已被使用，请重新试算");
        }
        MemberCardDetail oldDetail = cards.get(quote.oldCardId());
        if (oldDetail == null || !"ACTIVE".equals(oldDetail.card().status())
                || !oldDetail.card().version().equals(quote.oldCardVersion())) {
            throw new DuplicateResourceException("原次卡状态已发生变化，请重新试算");
        }
        if (oldDetail.balances().stream().anyMatch(item -> item.frozenTimes().signum() > 0)) {
            throw new IllegalArgumentException("原次卡存在冻结次数，不能换卡");
        }
        BigDecimal remaining = oldDetail.balances().stream().map(MemberCardBalanceItem::remainingTimes)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (remaining.compareTo(quote.oldRemainingTimes()) != 0) {
            throw new DuplicateResourceException("原次卡剩余次数已发生变化，请重新试算");
        }
        long exchangeId = exchangeIds.incrementAndGet();
        List<MemberCardBalanceItem> oldBalances = oldDetail.balances().stream().map(item ->
                new MemberCardBalanceItem(
                        item.id(), item.serviceId(), item.serviceCode(), item.serviceName(), item.totalTimes(),
                        BigDecimal.ZERO.setScale(4), item.frozenTimes(), item.deductTimes(), nextVersion(item.version())))
                .toList();
        List<MemberCardLedgerItem> oldLedgers = new ArrayList<>(oldDetail.ledgers());
        List<MemberCardBalanceItem> positiveOld = oldDetail.balances().stream()
                .filter(item -> item.remainingTimes().signum() > 0).toList();
        BigDecimal allocatedOld = BigDecimal.ZERO.setScale(4);
        for (int index = 0; index < positiveOld.size(); index++) {
            MemberCardBalanceItem balance = positiveOld.get(index);
            BigDecimal value = index == positiveOld.size() - 1
                    ? quote.oldRemainingValue().subtract(allocatedOld)
                    : quote.oldRemainingValue().multiply(balance.remainingTimes())
                            .divide(remaining, 4, java.math.RoundingMode.HALF_UP);
            value = value.setScale(4, java.math.RoundingMode.HALF_UP);
            allocatedOld = allocatedOld.add(value);
            oldLedgers.add(new MemberCardLedgerItem(
                    ledgerIds.incrementAndGet(), numbers.cardLedgerNo(), balance.serviceId(), balance.serviceName(),
                    "EXCHANGE_OUT", balance.remainingTimes(), balance.remainingTimes().negate(),
                    BigDecimal.ZERO.setScale(4), value, "CARD_EXCHANGE", exchangeId, LocalDateTime.now(),
                    "card-exchange:" + exchangeId + ":out:" + balance.id(), null, "换卡转出剩余次数"));
        }
        MemberCardSummary old = oldDetail.card();
        MemberCardSummary exchangedOld = new MemberCardSummary(
                old.id(), old.cardNo(), old.memberId(), old.cardTypeId(), old.cardTypeCode(), old.cardTypeName(),
                old.purchaseStoreId(), old.purchaseStoreName(), old.purchasePrice(), old.totalTimes(),
                BigDecimal.ZERO.setScale(4), old.frozenTimes(), old.startedAt(), old.expiresAt(),
                "EXCHANGED", nextVersion(old.version()));
        cards.put(old.id(), new MemberCardDetail(exchangedOld, oldBalances, List.copyOf(oldLedgers)));

        long newCardId = cardIds.incrementAndGet();
        BigDecimal targetTotal = command.targetCardType().serviceRules().stream().map(CardServiceRule::includedTimes)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        MemberCardSummary newCard = new MemberCardSummary(
                newCardId, numbers.memberCardNo(), command.memberId(), command.targetCardType().id(),
                command.targetCardType().code(), command.targetCardType().name(), command.storeId(), command.storeName(),
                command.targetCardType().salePrice(), targetTotal, targetTotal, BigDecimal.ZERO.setScale(4),
                command.startedAt(), command.startedAt().plusDays(command.targetCardType().validDays()), "ACTIVE", "1");
        List<MemberCardBalanceItem> newBalances = new ArrayList<>();
        List<MemberCardLedgerItem> newLedgers = new ArrayList<>();
        for (CardServiceRule rule : command.targetCardType().serviceRules()) {
            newBalances.add(new MemberCardBalanceItem(
                    balanceIds.incrementAndGet(), rule.serviceId(), rule.serviceCode(), rule.serviceName(),
                    rule.includedTimes(), rule.includedTimes(), BigDecimal.ZERO.setScale(4), rule.deductTimes(), "1"));
            newLedgers.add(new MemberCardLedgerItem(
                    ledgerIds.incrementAndGet(), numbers.cardLedgerNo(), rule.serviceId(), rule.serviceName(),
                    "EXCHANGE_IN", BigDecimal.ZERO.setScale(4), rule.includedTimes(), rule.includedTimes(),
                    allocatedValue(command.targetCardType().salePrice(), rule.includedTimes(), targetTotal),
                    "CARD_EXCHANGE", exchangeId, LocalDateTime.now(),
                    "card-exchange:" + exchangeId + ":in:" + rule.serviceId(), null, "换卡转入新卡次数"));
        }
        cards.put(newCardId, new MemberCardDetail(newCard, List.copyOf(newBalances), List.copyOf(newLedgers)));
        CardExchangeResult result = new CardExchangeResult(
                exchangeId, command.exchangeNo(), exchangedOld, newCard, quote.oldRemainingValue(),
                quote.newCardValue(), quote.differenceAmount(), command.payments(), LocalDateTime.now());
        exchanges.put(command.idempotencyKey(), result);
        exchangeQuotes.put(quote.quoteNo(), new CardExchangeQuote(
                quote.id(), quote.quoteNo(), quote.oldCardId(), quote.oldCardNo(), quote.oldCardTypeName(),
                quote.targetCardTypeId(), quote.targetCardTypeName(), quote.targetCardTypeVersion(), quote.oldRemainingTimes(),
                quote.oldRemainingValue(), quote.newCardValue(), quote.differenceAmount(), quote.oldCardVersion(),
                quote.expiresAt(), true));
        return result;
    }

    private BigDecimal allocatedValue(BigDecimal price, BigDecimal included, BigDecimal total) {
        return price.multiply(included).divide(total, 4, java.math.RoundingMode.HALF_UP);
    }

    private String nextVersion(String version) { return Long.toString(Long.parseLong(version) + 1); }
}
