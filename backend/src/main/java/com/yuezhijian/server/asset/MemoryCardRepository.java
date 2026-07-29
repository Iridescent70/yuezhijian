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
    private final Map<Long, Long> saleEmployees = new LinkedHashMap<>();
    private final Map<String, CardSaleResult> sales = new LinkedHashMap<>();
    private final Map<String, CardExchangeQuote> exchangeQuotes = new LinkedHashMap<>();
    private final Map<String, CardExchangeResult> exchanges = new LinkedHashMap<>();
    private final Map<String, CardTransferResult> transfers = new LinkedHashMap<>();
    private final Map<Long, CardConsumptionRepriceItem> consumptionReprices = new LinkedHashMap<>();
    private final Map<String, CardRefundQuote> refundQuotes = new LinkedHashMap<>();
    private final Map<Long, CardRefundRequestDetail> refundRequests = new LinkedHashMap<>();
    private final Map<String, Long> refundRequestKeys = new LinkedHashMap<>();
    private final Map<String, Long> refundExecutionKeys = new LinkedHashMap<>();
    private final AtomicLong typeIds = new AtomicLong(501);
    private final AtomicLong orderIds = new AtomicLong(600);
    private final AtomicLong cardIds = new AtomicLong(700);
    private final AtomicLong balanceIds = new AtomicLong(800);
    private final AtomicLong ledgerIds = new AtomicLong(900);
    private final AtomicLong exchangeQuoteIds = new AtomicLong(1000);
    private final AtomicLong exchangeIds = new AtomicLong(1100);
    private final AtomicLong transferIds = new AtomicLong(1200);
    private final AtomicLong refundQuoteIds = new AtomicLong(1300);
    private final AtomicLong refundRequestIds = new AtomicLong(1400);
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
    public synchronized Optional<Long> saleEmployeeId(long memberCardId) {
        return Optional.ofNullable(saleEmployees.get(memberCardId));
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
            if (draft.salesEmployeeId() != null) saleEmployees.put(cardId, draft.salesEmployeeId());
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
        long ledgerId = ledgerIds.incrementAndGet();
        LocalDateTime consumedAt = LocalDateTime.now();
        ledgers.add(new MemberCardLedgerItem(
                ledgerId, numbers.cardLedgerNo(), command.serviceId(), current.serviceName(),
                "CONSUME", current.remainingTimes(), command.times().negate(), after, command.amount(),
                "BILL", command.billId(), consumedAt,
                "bill:" + command.billId() + ":line:" + command.billLineId(), null, command.displayName()));
        consumptionReprices.put(ledgerId, new CardConsumptionRepriceItem(
                ledgerId, command.billId(), "BILL-" + command.billId(), command.serviceId(),
                current.serviceName(), consumedAt, command.originalAmount()));
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
    public synchronized List<CardConsumptionRepriceItem> consumptionRepriceItems(long memberCardId) {
        MemberCardDetail detail = cards.get(memberCardId);
        if (detail == null) return List.of();
        java.util.Set<Long> reversed = detail.ledgers().stream()
                .map(MemberCardLedgerItem::reversedLedgerId).filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());
        return detail.ledgers().stream()
                .filter(item -> "CONSUME".equals(item.transactionType()) && !reversed.contains(item.id()))
                .map(item -> consumptionReprices.get(item.id())).filter(java.util.Objects::nonNull)
                .sorted(Comparator.comparing(CardConsumptionRepriceItem::consumedAt)
                        .thenComparing(CardConsumptionRepriceItem::cardLedgerId))
                .toList();
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
        if (command.employeeId() != null) saleEmployees.put(newCardId, command.employeeId());
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

    @Override
    public synchronized Optional<CardTransferResult> findTransferByIdempotencyKey(String key) {
        return Optional.ofNullable(transfers.get(key));
    }

    @Override
    public synchronized CardTransferResult transfer(CardTransferCommand command) {
        Optional<CardTransferResult> existing = findTransferByIdempotencyKey(command.idempotencyKey());
        if (existing.isPresent()) return existing.get();
        MemberCardDetail sourceDetail = cards.get(command.sourceCard().id());
        if (sourceDetail == null || !"ACTIVE".equals(sourceDetail.card().status())
                || !sourceDetail.card().version().equals(command.sourceCard().version())) {
            throw new DuplicateResourceException("原次卡状态已发生变化，请刷新后重试");
        }
        if (sourceDetail.balances().stream().anyMatch(item -> item.frozenTimes().signum() > 0)) {
            throw new IllegalArgumentException("原次卡存在冻结次数，不能转赠");
        }
        List<MemberCardBalanceItem> transferable = sourceDetail.balances().stream()
                .filter(item -> item.remainingTimes().signum() > 0).toList();
        BigDecimal remaining = transferable.stream().map(MemberCardBalanceItem::remainingTimes)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (remaining.signum() <= 0 || remaining.compareTo(command.remainingTimes()) != 0) {
            throw new DuplicateResourceException("原次卡次数已发生变化，请刷新后重试");
        }
        long transferId = transferIds.incrementAndGet();
        List<MemberCardBalanceItem> sourceBalances = sourceDetail.balances().stream().map(item ->
                new MemberCardBalanceItem(
                        item.id(), item.serviceId(), item.serviceCode(), item.serviceName(), item.totalTimes(),
                        BigDecimal.ZERO.setScale(4), item.frozenTimes(), item.deductTimes(), nextVersion(item.version())))
                .toList();
        List<MemberCardLedgerItem> sourceLedgers = new ArrayList<>(sourceDetail.ledgers());
        List<MemberCardBalanceItem> targetBalances = new ArrayList<>();
        List<MemberCardLedgerItem> targetLedgers = new ArrayList<>();
        BigDecimal allocated = BigDecimal.ZERO.setScale(4);
        for (int index = 0; index < transferable.size(); index++) {
            MemberCardBalanceItem balance = transferable.get(index);
            BigDecimal value = index == transferable.size() - 1
                    ? command.remainingValue().subtract(allocated)
                    : command.remainingValue().multiply(balance.remainingTimes())
                            .divide(remaining, 4, java.math.RoundingMode.HALF_UP);
            value = value.setScale(4, java.math.RoundingMode.HALF_UP);
            allocated = allocated.add(value);
            sourceLedgers.add(new MemberCardLedgerItem(
                    ledgerIds.incrementAndGet(), numbers.cardLedgerNo(), balance.serviceId(), balance.serviceName(),
                    "TRANSFER_OUT", balance.remainingTimes(), balance.remainingTimes().negate(),
                    BigDecimal.ZERO.setScale(4), value, "CARD_TRANSFER", transferId, command.executedAt(),
                    "card-transfer:" + transferId + ":out:" + balance.id(), null, "次卡转赠转出"));
            targetBalances.add(new MemberCardBalanceItem(
                    balanceIds.incrementAndGet(), balance.serviceId(), balance.serviceCode(), balance.serviceName(),
                    balance.remainingTimes(), balance.remainingTimes(), BigDecimal.ZERO.setScale(4),
                    balance.deductTimes(), "1"));
            targetLedgers.add(new MemberCardLedgerItem(
                    ledgerIds.incrementAndGet(), numbers.cardLedgerNo(), balance.serviceId(), balance.serviceName(),
                    "TRANSFER_IN", BigDecimal.ZERO.setScale(4), balance.remainingTimes(), balance.remainingTimes(),
                    value, "CARD_TRANSFER", transferId, command.executedAt(),
                    "card-transfer:" + transferId + ":in:" + balance.serviceId(), null, "次卡转赠转入"));
        }
        MemberCardSummary source = sourceDetail.card();
        MemberCardSummary transferredSource = new MemberCardSummary(
                source.id(), source.cardNo(), source.memberId(), source.cardTypeId(), source.cardTypeCode(),
                source.cardTypeName(), source.purchaseStoreId(), source.purchaseStoreName(), source.purchasePrice(),
                source.totalTimes(), BigDecimal.ZERO.setScale(4), source.frozenTimes(), source.startedAt(),
                source.expiresAt(), "TRANSFERRED", nextVersion(source.version()));
        cards.put(source.id(), new MemberCardDetail(
                transferredSource, sourceBalances, List.copyOf(sourceLedgers)));

        long targetCardId = cardIds.incrementAndGet();
        MemberCardSummary target = new MemberCardSummary(
                targetCardId, numbers.memberCardNo(), command.recipientMemberId(), source.cardTypeId(),
                source.cardTypeCode(), source.cardTypeName(), source.purchaseStoreId(), source.purchaseStoreName(),
                command.remainingValue(), command.remainingTimes(), command.remainingTimes(),
                BigDecimal.ZERO.setScale(4), command.executedAt(), command.newExpiresAt(), "ACTIVE", "1");
        cards.put(targetCardId, new MemberCardDetail(target, List.copyOf(targetBalances), List.copyOf(targetLedgers)));
        CardTransferResult result = new CardTransferResult(
                transferId, command.transferNo(), transferredSource, target, source.memberId(),
                command.recipientMemberId(), command.recipientMemberName(), command.remainingTimes(),
                command.remainingValue(), source.expiresAt(), command.newExpiresAt(), command.reason(),
                command.executedAt());
        transfers.put(command.idempotencyKey(), result);
        return result;
    }

    @Override
    public synchronized CardRefundQuote createRefundQuote(CardRefundQuoteDraft draft) {
        long id = refundQuoteIds.incrementAndGet();
        CardRefundQuote result = new CardRefundQuote(
                id, draft.quoteNo(), draft.card().id(), draft.card().cardNo(), draft.card().cardTypeName(),
                draft.card().memberId(), draft.originalAmount(), draft.consumedRepriceAmount(),
                draft.feeAmount(), draft.refundAmount(), draft.card().version(), draft.items(),
                draft.expiresAt(), false);
        refundQuotes.put(result.quoteNo(), result);
        return result;
    }

    @Override
    public synchronized Optional<CardRefundQuote> findRefundQuote(String quoteNo) {
        return Optional.ofNullable(refundQuotes.get(quoteNo));
    }

    @Override
    public synchronized List<CardRefundRequestSummary> refundRequests(String status) {
        return refundRequests.values().stream().map(CardRefundRequestDetail::request)
                .filter(item -> status == null || status.equals(item.status()))
                .sorted(Comparator.comparing(CardRefundRequestSummary::requestedAt).reversed()
                        .thenComparing(CardRefundRequestSummary::id, Comparator.reverseOrder()))
                .toList();
    }

    @Override
    public synchronized Optional<CardRefundRequestDetail> findRefundRequest(long id) {
        return Optional.ofNullable(refundRequests.get(id));
    }

    @Override
    public synchronized Optional<CardRefundRequestDetail> findRefundRequestByRequestKey(String key) {
        Long id = key == null ? null : refundRequestKeys.get(key);
        return id == null ? Optional.empty() : findRefundRequest(id);
    }

    @Override
    public synchronized Optional<CardRefundRequestDetail> findRefundRequestByExecutionKey(String key) {
        Long id = key == null ? null : refundExecutionKeys.get(key);
        return id == null ? Optional.empty() : findRefundRequest(id);
    }

    @Override
    public synchronized Optional<CardRefundRequestDetail> findActiveRefundRequest(long memberCardId) {
        return refundRequests.values().stream()
                .filter(item -> item.request().memberCardId() == memberCardId)
                .filter(item -> List.of("SUBMITTED", "APPROVED", "EXECUTED").contains(item.request().status()))
                .findFirst();
    }

    @Override
    public synchronized CardRefundRequestDetail submitRefundRequest(CardRefundSubmission submission) {
        Optional<CardRefundRequestDetail> existing = findRefundRequestByRequestKey(submission.idempotencyKey());
        if (existing.isPresent()) return existing.get();
        CardRefundQuote quote = refundQuotes.get(submission.quote().quoteNo());
        if (quote == null || quote.used() || quote.expiresAt().isBefore(LocalDateTime.now())) {
            throw new DuplicateResourceException("退卡试算已失效或已被使用，请重新试算");
        }
        if (findActiveRefundRequest(quote.memberCardId()).isPresent()) {
            throw new DuplicateResourceException("当前次卡已有待处理或已执行的退卡申请");
        }
        MemberCardDetail cardDetail = cards.get(quote.memberCardId());
        if (cardDetail == null || !"ACTIVE".equals(cardDetail.card().status())
                || !cardDetail.card().version().equals(quote.cardVersion())) {
            throw new DuplicateResourceException("次卡状态已发生变化，请重新试算");
        }
        MemberCardSummary card = cardDetail.card();
        MemberCardSummary frozen = new MemberCardSummary(
                card.id(), card.cardNo(), card.memberId(), card.cardTypeId(), card.cardTypeCode(), card.cardTypeName(),
                card.purchaseStoreId(), card.purchaseStoreName(), card.purchasePrice(), card.totalTimes(),
                card.remainingTimes(), card.frozenTimes(), card.startedAt(), card.expiresAt(),
                "FROZEN", nextVersion(card.version()));
        cards.put(card.id(), new MemberCardDetail(frozen, cardDetail.balances(), cardDetail.ledgers()));
        long id = refundRequestIds.incrementAndGet();
        LocalDateTime now = LocalDateTime.now();
        CardRefundRequestSummary summary = new CardRefundRequestSummary(
                id, quote.id(), submission.requestNo(), quote.memberCardId(), quote.cardNo(), quote.cardTypeName(),
                quote.memberId(), submission.memberName(), submission.storeName(), quote.originalAmount(),
                quote.consumedRepriceAmount(), quote.feeAmount(), quote.refundAmount(), submission.refundMethodId(),
                submission.refundMethodName(), submission.refundMethodRequiresReference(), "SUBMITTED",
                "PENDING_MODULE", submission.reason(), now, submission.operatorId(), null, null, null, null,
                frozen.version(), "1");
        CardRefundRequestDetail result = new CardRefundRequestDetail(summary, quote.items(), null);
        refundRequests.put(id, result);
        refundRequestKeys.put(submission.idempotencyKey(), id);
        refundQuotes.put(quote.quoteNo(), new CardRefundQuote(
                quote.id(), quote.quoteNo(), quote.memberCardId(), quote.cardNo(), quote.cardTypeName(), quote.memberId(),
                quote.originalAmount(), quote.consumedRepriceAmount(), quote.feeAmount(), quote.refundAmount(),
                quote.cardVersion(), quote.items(), quote.expiresAt(), true));
        return result;
    }

    @Override
    public synchronized CardRefundRequestDetail reviewRefundRequest(CardRefundReviewCommand command) {
        CardRefundRequestDetail current = refundRequests.get(command.request().request().id());
        if (current == null || !"SUBMITTED".equals(current.request().status())
                || !current.request().version().equals(command.version())) {
            throw new DuplicateResourceException("退卡申请已被他人处理，请刷新后重试");
        }
        CardRefundRequestSummary old = current.request();
        if (!command.approved()) {
            MemberCardDetail cardDetail = cards.get(old.memberCardId());
            if (cardDetail == null || !"FROZEN".equals(cardDetail.card().status())
                    || !cardDetail.card().version().equals(old.cardVersion())) {
                throw new DuplicateResourceException("次卡状态已发生变化，无法驳回并恢复");
            }
            MemberCardSummary card = cardDetail.card();
            MemberCardSummary active = new MemberCardSummary(
                    card.id(), card.cardNo(), card.memberId(), card.cardTypeId(), card.cardTypeCode(),
                    card.cardTypeName(), card.purchaseStoreId(), card.purchaseStoreName(), card.purchasePrice(),
                    card.totalTimes(), card.remainingTimes(), card.frozenTimes(), card.startedAt(), card.expiresAt(),
                    "ACTIVE", nextVersion(card.version()));
            cards.put(card.id(), new MemberCardDetail(active, cardDetail.balances(), cardDetail.ledgers()));
        }
        CardRefundRequestSummary reviewed = new CardRefundRequestSummary(
                old.id(), old.quoteId(), old.requestNo(), old.memberCardId(), old.cardNo(), old.cardTypeName(),
                old.memberId(), old.memberName(), old.storeName(), old.originalAmount(), old.consumedRepriceAmount(),
                old.feeAmount(), old.refundAmount(), old.refundMethodId(), old.refundMethodName(),
                old.refundMethodRequiresReference(), command.approved() ? "APPROVED" : "REJECTED",
                old.commissionAdjustmentStatus(), old.reason(), old.requestedAt(), old.requestedBy(),
                LocalDateTime.now(), command.operatorId(), command.comment(), null, old.cardVersion(),
                nextVersion(old.version()));
        CardRefundRequestDetail result = new CardRefundRequestDetail(reviewed, current.consumedItems(), null);
        refundRequests.put(old.id(), result);
        return result;
    }

    @Override
    public synchronized CardRefundRequestDetail executeRefund(CardRefundExecutionCommand command) {
        Optional<CardRefundRequestDetail> existing = findRefundRequestByExecutionKey(command.idempotencyKey());
        if (existing.isPresent()) return existing.get();
        CardRefundRequestDetail current = refundRequests.get(command.request().request().id());
        if (current == null || !"APPROVED".equals(current.request().status())
                || !current.request().version().equals(command.version())) {
            throw new DuplicateResourceException("退卡申请已被他人处理，请刷新后重试");
        }
        CardRefundRequestSummary old = current.request();
        MemberCardDetail cardDetail = cards.get(old.memberCardId());
        if (cardDetail == null || !"FROZEN".equals(cardDetail.card().status())
                || !cardDetail.card().version().equals(old.cardVersion())) {
            throw new DuplicateResourceException("次卡状态已发生变化，无法执行退卡");
        }
        List<MemberCardBalanceItem> remaining = cardDetail.balances().stream()
                .filter(item -> item.remainingTimes().signum() > 0).toList();
        BigDecimal totalTimes = remaining.stream().map(MemberCardBalanceItem::remainingTimes)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (totalTimes.signum() <= 0) throw new DuplicateResourceException("次卡已无可退剩余次数");
        List<MemberCardBalanceItem> balances = cardDetail.balances().stream().map(item ->
                new MemberCardBalanceItem(
                        item.id(), item.serviceId(), item.serviceCode(), item.serviceName(), item.totalTimes(),
                        BigDecimal.ZERO.setScale(4), item.frozenTimes(), item.deductTimes(), nextVersion(item.version())))
                .toList();
        List<MemberCardLedgerItem> ledgers = new ArrayList<>(cardDetail.ledgers());
        BigDecimal allocated = BigDecimal.ZERO.setScale(4);
        for (int index = 0; index < remaining.size(); index++) {
            MemberCardBalanceItem balance = remaining.get(index);
            BigDecimal value = index == remaining.size() - 1
                    ? old.refundAmount().subtract(allocated)
                    : old.refundAmount().multiply(balance.remainingTimes())
                            .divide(totalTimes, 4, java.math.RoundingMode.HALF_UP);
            value = value.setScale(4, java.math.RoundingMode.HALF_UP);
            allocated = allocated.add(value);
            ledgers.add(new MemberCardLedgerItem(
                    ledgerIds.incrementAndGet(), numbers.cardLedgerNo(), balance.serviceId(), balance.serviceName(),
                    "REFUND_OUT", balance.remainingTimes(), balance.remainingTimes().negate(),
                    BigDecimal.ZERO.setScale(4), value, "CARD_REFUND", old.id(), LocalDateTime.now(),
                    "card-refund:" + old.id() + ":balance:" + balance.id(), null, "退卡清零剩余次数"));
        }
        MemberCardSummary card = cardDetail.card();
        MemberCardSummary refunded = new MemberCardSummary(
                card.id(), card.cardNo(), card.memberId(), card.cardTypeId(), card.cardTypeCode(), card.cardTypeName(),
                card.purchaseStoreId(), card.purchaseStoreName(), card.purchasePrice(), card.totalTimes(),
                BigDecimal.ZERO.setScale(4), card.frozenTimes(), card.startedAt(), card.expiresAt(),
                "REFUNDED", nextVersion(card.version()));
        cards.put(card.id(), new MemberCardDetail(refunded, balances, List.copyOf(ledgers)));
        LocalDateTime executedAt = LocalDateTime.now();
        CardRefundPayment payment = old.refundAmount().signum() == 0 ? null : new CardRefundPayment(
                old.refundMethodId(), old.refundMethodName(), old.refundAmount(), "SUCCESS",
                command.externalRefundReference(), executedAt);
        CardRefundRequestSummary executed = new CardRefundRequestSummary(
                old.id(), old.quoteId(), old.requestNo(), old.memberCardId(), old.cardNo(), old.cardTypeName(),
                old.memberId(), old.memberName(), old.storeName(), old.originalAmount(), old.consumedRepriceAmount(),
                old.feeAmount(), old.refundAmount(), old.refundMethodId(), old.refundMethodName(),
                old.refundMethodRequiresReference(), "EXECUTED", old.commissionAdjustmentStatus(), old.reason(),
                old.requestedAt(), old.requestedBy(), old.reviewedAt(), old.reviewedBy(), old.reviewComment(),
                executedAt, old.cardVersion(), nextVersion(old.version()));
        CardRefundRequestDetail result = new CardRefundRequestDetail(executed, current.consumedItems(), payment);
        refundRequests.put(old.id(), result);
        refundExecutionKeys.put(command.idempotencyKey(), old.id());
        return result;
    }

    @Override
    public synchronized CardRefundRequestDetail updateRefundCommissionStatus(
            long requestId, String status, long operatorId) {
        CardRefundRequestDetail current = refundRequests.get(requestId);
        if (current == null || !"EXECUTED".equals(current.request().status())) {
            throw new DuplicateResourceException("退卡申请状态已发生变化，无法更新提成冲回状态");
        }
        if (status.equals(current.request().commissionAdjustmentStatus())) return current;
        CardRefundRequestSummary old = current.request();
        CardRefundRequestSummary updated = new CardRefundRequestSummary(
                old.id(), old.quoteId(), old.requestNo(), old.memberCardId(), old.cardNo(), old.cardTypeName(),
                old.memberId(), old.memberName(), old.storeName(), old.originalAmount(), old.consumedRepriceAmount(),
                old.feeAmount(), old.refundAmount(), old.refundMethodId(), old.refundMethodName(),
                old.refundMethodRequiresReference(), old.status(), status, old.reason(), old.requestedAt(),
                old.requestedBy(), old.reviewedAt(), old.reviewedBy(), old.reviewComment(), old.executedAt(),
                old.cardVersion(), nextVersion(old.version()));
        CardRefundRequestDetail result = new CardRefundRequestDetail(updated, current.consumedItems(), current.payment());
        refundRequests.put(requestId, result);
        return result;
    }

    private BigDecimal allocatedValue(BigDecimal price, BigDecimal included, BigDecimal total) {
        return price.multiply(included).divide(total, 4, java.math.RoundingMode.HALF_UP);
    }

    private String nextVersion(String version) { return Long.toString(Long.parseLong(version) + 1); }
}
