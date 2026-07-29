package com.yuezhijian.server.asset;

import com.yuezhijian.server.common.DuplicateResourceException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Base64;
import java.util.Arrays;
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

    @Override
    @Transactional
    public void consumeCard(CardSettlementConsumption command) {
        MemberCardDetail card = findMemberCard(command.memberCardId())
                .orElseThrow(() -> new IllegalArgumentException("会员次卡不存在"));
        if (card.card().memberId() != command.memberId() || !"ACTIVE".equals(card.card().status())
                || card.card().expiresAt().isBefore(java.time.LocalDateTime.now())) {
            throw new IllegalArgumentException("会员次卡已失效或不属于当前会员");
        }
        MemberCardBalanceRow balance = mapper.lockMemberCardBalance(command.memberCardBalanceId());
        if (balance == null || balance.memberCardId() != command.memberCardId()
                || balance.serviceId() != command.serviceId()) {
            throw new IllegalArgumentException("次卡不支持当前项目");
        }
        byte[] expected;
        try { expected = Base64.getDecoder().decode(command.balanceVersion()); }
        catch (IllegalArgumentException exception) { throw new IllegalArgumentException("次卡版本格式不正确"); }
        if (!Arrays.equals(expected, balance.rowVersion())) {
            throw new DuplicateResourceException("次卡次数已发生变化，请重新试算");
        }
        if (balance.remainingTimes().compareTo(command.times()) < 0) throw new IllegalArgumentException("次卡剩余次数不足");
        BigDecimal after = balance.remainingTimes().subtract(command.times());
        if (mapper.consumeCardBalance(balance.id(), command.times(), balance.rowVersion()) != 1) {
            throw new DuplicateResourceException("次卡次数已发生变化，请重新试算");
        }
        long ledgerId = mapper.insertCardConsumeLedger(
                numbers.cardLedgerNo(), command, balance.remainingTimes(), after);
        mapper.refreshMemberCardStatus(command.memberCardId(), command.operatorId());
        mapper.insertCardAssetUsage(command, ledgerId);
    }

    @Override
    @Transactional
    public void refundCard(CardRefundCommand command) {
        if (command.times() == null || command.times().signum() <= 0 || command.originalLedgerId() == null) {
            throw new IllegalArgumentException("次卡冲销数据不完整");
        }
        MemberCardDetail card = findMemberCard(command.memberCardId())
                .orElseThrow(() -> new IllegalArgumentException("会员次卡不存在"));
        if (card.card().memberId() != command.memberId()) {
            throw new IllegalArgumentException("次卡不属于当前会员");
        }
        MemberCardBalanceRow balance = mapper.lockMemberCardBalance(command.memberCardBalanceId());
        if (balance == null || balance.memberCardId() != command.memberCardId()
                || balance.serviceId() != command.serviceId()) {
            throw new IllegalArgumentException("次卡冲销项目不匹配");
        }
        BigDecimal after = balance.remainingTimes().add(command.times());
        if (after.compareTo(balance.totalTimes().subtract(balance.frozenTimes())) > 0) {
            throw new IllegalArgumentException("次卡返还后次数超过可用上限");
        }
        if (mapper.refundCardBalance(balance.id(), command.times(), balance.rowVersion()) != 1) {
            throw new DuplicateResourceException("次卡次数已发生变化，请重新执行冲销");
        }
        mapper.insertCardRefundLedger(numbers.cardLedgerNo(), command, balance.remainingTimes(), after);
        mapper.restoreMemberCardStatus(command.memberCardId(), command.operatorId());
    }

    @Override
    public List<CardConsumptionRepriceItem> consumptionRepriceItems(long memberCardId) {
        return mapper.findConsumptionRepriceItems(memberCardId);
    }

    @Override
    public Optional<CardExchangeQuote> findExchangeQuote(String quoteNo) {
        if (quoteNo == null) return Optional.empty();
        return Optional.ofNullable(mapper.findExchangeQuote(quoteNo));
    }

    @Override
    @Transactional
    public CardExchangeQuote createExchangeQuote(CardExchangeQuoteDraft draft) {
        mapper.insertExchangeQuote(draft);
        return findExchangeQuote(draft.quoteNo()).orElseThrow();
    }

    @Override
    public Optional<CardExchangeResult> findExchangeByIdempotencyKey(String key) {
        if (key == null) return Optional.empty();
        CardExchangeRow row = mapper.findExchangeByIdempotencyKey(key);
        if (row == null) return Optional.empty();
        MemberCardSummary oldCard = findMemberCard(row.oldCardId()).orElseThrow().card();
        MemberCardSummary newCard = findMemberCard(row.newCardId()).orElseThrow().card();
        return Optional.of(new CardExchangeResult(
                row.id(), row.exchangeNo(), oldCard, newCard, row.oldRemainingValue(), row.newCardValue(),
                row.differenceAmount(), mapper.findExchangePayments(row.id()), row.executedAt()));
    }

    @Override
    @Transactional
    public CardExchangeResult exchange(CardExchangeCommand command) {
        Optional<CardExchangeResult> existing = findExchangeByIdempotencyKey(command.idempotencyKey());
        if (existing.isPresent()) return existing.get();
        if (mapper.markExchangeQuoteUsed(command.quote().id(), command.quote().oldCardVersion()) != 1) {
            throw new DuplicateResourceException("换卡试算已失效或已被使用，请重新试算");
        }
        List<MemberCardBalanceRow> oldBalances = mapper.lockMemberCardBalances(command.quote().oldCardId());
        if (oldBalances.isEmpty()) throw new IllegalArgumentException("原次卡没有项目余额");
        if (oldBalances.stream().anyMatch(item -> item.frozenTimes().signum() > 0)) {
            throw new IllegalArgumentException("原次卡存在冻结次数，不能换卡");
        }
        BigDecimal remaining = oldBalances.stream().map(MemberCardBalanceRow::remainingTimes)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (remaining.compareTo(command.quote().oldRemainingTimes()) != 0) {
            throw new DuplicateResourceException("原次卡剩余次数已发生变化，请重新试算");
        }
        Long newCardId = mapper.insertExchangeMemberCard(numbers.memberCardNo(), command);
        if (newCardId == null) {
            throw new DuplicateResourceException("目标次卡配置已发生变化，请重新试算");
        }
        long exchangeId = mapper.insertExchange(command, newCardId);

        BigDecimal allocatedOld = BigDecimal.ZERO.setScale(4);
        List<MemberCardBalanceRow> positiveOld = oldBalances.stream()
                .filter(item -> item.remainingTimes().signum() > 0).toList();
        for (int index = 0; index < positiveOld.size(); index++) {
            MemberCardBalanceRow balance = positiveOld.get(index);
            BigDecimal value = index == positiveOld.size() - 1
                    ? command.quote().oldRemainingValue().subtract(allocatedOld)
                    : command.quote().oldRemainingValue().multiply(balance.remainingTimes())
                            .divide(remaining, 4, RoundingMode.HALF_UP);
            value = value.setScale(4, RoundingMode.HALF_UP);
            allocatedOld = allocatedOld.add(value);
            if (mapper.clearCardBalance(balance.id(), balance.rowVersion()) != 1) {
                throw new DuplicateResourceException("原次卡次数已发生变化，请重新试算");
            }
            mapper.insertExchangeOutLedger(
                    numbers.cardLedgerNo(), exchangeId, balance, value, command.operatorId());
        }

        BigDecimal targetTotal = command.targetCardType().serviceRules().stream()
                .map(CardServiceRule::includedTimes).reduce(BigDecimal.ZERO, BigDecimal::add);
        for (CardServiceRule rule : command.targetCardType().serviceRules()) {
            mapper.insertMemberCardBalance(newCardId, rule);
            mapper.insertExchangeInLedger(
                    numbers.cardLedgerNo(), exchangeId, newCardId, rule,
                    allocatedValue(command.targetCardType().salePrice(), rule.includedTimes(), targetTotal),
                    command.operatorId());
        }
        for (int index = 0; index < command.payments().size(); index++) {
            mapper.insertExchangePayment(exchangeId, command.payments().get(index), (index + 1) * 10);
        }
        if (mapper.markCardExchanged(
                command.quote().oldCardId(), command.quote().oldCardVersion(), command.operatorId()) != 1) {
            throw new DuplicateResourceException("原次卡状态已发生变化，请重新试算");
        }
        return findExchangeByIdempotencyKey(command.idempotencyKey()).orElseThrow();
    }

    @Override
    public Optional<CardTransferResult> findTransferByIdempotencyKey(String key) {
        if (key == null) return Optional.empty();
        CardTransferRow row = mapper.findTransferByIdempotencyKey(key);
        if (row == null) return Optional.empty();
        MemberCardSummary sourceCard = findMemberCard(row.sourceCardId()).orElseThrow().card();
        MemberCardSummary targetCard = findMemberCard(row.targetCardId()).orElseThrow().card();
        return Optional.of(new CardTransferResult(
                row.id(), row.transferNo(), sourceCard, targetCard, row.sourceMemberId(), row.recipientMemberId(),
                row.recipientMemberName(), row.remainingTimes(), row.remainingValue(), row.oldExpiresAt(),
                row.newExpiresAt(), row.reason(), row.executedAt()));
    }

    @Override
    @Transactional
    public CardTransferResult transfer(CardTransferCommand command) {
        Optional<CardTransferResult> existing = findTransferByIdempotencyKey(command.idempotencyKey());
        if (existing.isPresent()) return existing.get();
        List<MemberCardBalanceRow> balances = mapper.lockMemberCardBalances(command.sourceCard().id());
        List<MemberCardBalanceRow> transferable = balances.stream()
                .filter(item -> item.remainingTimes().signum() > 0).toList();
        if (transferable.isEmpty()) throw new IllegalArgumentException("原次卡没有可转赠的剩余次数");
        if (balances.stream().anyMatch(item -> item.frozenTimes().signum() > 0)) {
            throw new IllegalArgumentException("原次卡存在冻结次数，不能转赠");
        }
        BigDecimal remaining = transferable.stream().map(MemberCardBalanceRow::remainingTimes)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (remaining.compareTo(command.remainingTimes()) != 0) {
            throw new DuplicateResourceException("原次卡次数已发生变化，请刷新后重试");
        }
        Long targetCardId = mapper.insertTransferMemberCard(numbers.memberCardNo(), command);
        if (targetCardId == null) throw new DuplicateResourceException("接收会员状态已发生变化，请重新选择");
        long transferId = mapper.insertCardTransfer(command, targetCardId);
        BigDecimal allocated = BigDecimal.ZERO.setScale(4);
        for (int index = 0; index < transferable.size(); index++) {
            MemberCardBalanceRow balance = transferable.get(index);
            BigDecimal value = index == transferable.size() - 1
                    ? command.remainingValue().subtract(allocated)
                    : command.remainingValue().multiply(balance.remainingTimes())
                            .divide(remaining, 4, RoundingMode.HALF_UP);
            value = value.setScale(4, RoundingMode.HALF_UP);
            allocated = allocated.add(value);
            if (mapper.clearCardBalance(balance.id(), balance.rowVersion()) != 1) {
                throw new DuplicateResourceException("原次卡次数已发生变化，请刷新后重试");
            }
            mapper.insertTransferBalance(targetCardId, balance);
            mapper.insertTransferOutLedger(
                    numbers.cardLedgerNo(), transferId, balance, value, command.executedAt(), command.operatorId());
            mapper.insertTransferInLedger(
                    numbers.cardLedgerNo(), transferId, targetCardId, balance, value,
                    command.executedAt(), command.operatorId());
        }
        if (mapper.markCardTransferred(
                command.sourceCard().id(), command.sourceCard().version(), command.operatorId()) != 1) {
            throw new DuplicateResourceException("原次卡状态已发生变化，请刷新后重试");
        }
        return findTransferByIdempotencyKey(command.idempotencyKey()).orElseThrow();
    }

    @Override
    @Transactional
    public CardRefundQuote createRefundQuote(CardRefundQuoteDraft draft) {
        long quoteId = mapper.insertRefundQuote(draft);
        for (int index = 0; index < draft.items().size(); index++) {
            mapper.insertRefundQuoteItem(quoteId, draft.items().get(index), (index + 1) * 10);
        }
        return findRefundQuote(draft.quoteNo()).orElseThrow();
    }

    @Override
    public Optional<CardRefundQuote> findRefundQuote(String quoteNo) {
        if (quoteNo == null) return Optional.empty();
        CardRefundQuoteRow row = mapper.findRefundQuote(quoteNo);
        if (row == null) return Optional.empty();
        return Optional.of(new CardRefundQuote(
                row.id(), row.quoteNo(), row.memberCardId(), row.cardNo(), row.cardTypeName(), row.memberId(),
                row.originalAmount(), row.consumedRepriceAmount(), row.feeAmount(), row.refundAmount(),
                row.cardVersion(), mapper.findRefundQuoteItems(row.id()), row.expiresAt(), row.used()));
    }

    @Override
    public List<CardRefundRequestSummary> refundRequests(String status) {
        return mapper.findRefundRequests(status);
    }

    @Override
    public Optional<CardRefundRequestDetail> findRefundRequest(long id) {
        return detail(mapper.findRefundRequest(id));
    }

    @Override
    public Optional<CardRefundRequestDetail> findRefundRequestByRequestKey(String key) {
        return key == null ? Optional.empty() : detail(mapper.findRefundRequestByRequestKey(key));
    }

    @Override
    public Optional<CardRefundRequestDetail> findRefundRequestByExecutionKey(String key) {
        return key == null ? Optional.empty() : detail(mapper.findRefundRequestByExecutionKey(key));
    }

    @Override
    public Optional<CardRefundRequestDetail> findActiveRefundRequest(long memberCardId) {
        return detail(mapper.findActiveRefundRequest(memberCardId));
    }

    @Override
    @Transactional
    public CardRefundRequestDetail submitRefundRequest(CardRefundSubmission submission) {
        Optional<CardRefundRequestDetail> existing = findRefundRequestByRequestKey(submission.idempotencyKey());
        if (existing.isPresent()) return existing.get();
        if (mapper.markRefundQuoteUsed(
                submission.quote().id(), submission.quote().cardVersion()) != 1) {
            throw new DuplicateResourceException("退卡试算已失效或已被使用，请重新试算");
        }
        if (mapper.freezeCardForRefund(
                submission.quote().memberCardId(), submission.quote().cardVersion(), submission.operatorId()) != 1) {
            throw new DuplicateResourceException("次卡状态已发生变化，请重新试算");
        }
        String frozenVersion = findMemberCard(submission.quote().memberCardId()).orElseThrow().card().version();
        long id = mapper.insertRefundRequest(submission, frozenVersion);
        return findRefundRequest(id).orElseThrow();
    }

    @Override
    @Transactional
    public CardRefundRequestDetail reviewRefundRequest(CardRefundReviewCommand command) {
        CardRefundRequestSummary request = command.request().request();
        String status = command.approved() ? "APPROVED" : "REJECTED";
        if (mapper.reviewCardRefund(
                request.id(), status, command.comment(), command.version(), command.operatorId()) != 1) {
            throw new DuplicateResourceException("退卡申请已被他人处理，请刷新后重试");
        }
        if (!command.approved() && mapper.unfreezeRejectedCard(
                request.memberCardId(), request.cardVersion(), command.operatorId()) != 1) {
            throw new DuplicateResourceException("次卡状态已发生变化，无法驳回并恢复");
        }
        return findRefundRequest(request.id()).orElseThrow();
    }

    @Override
    @Transactional
    public CardRefundRequestDetail executeRefund(CardRefundExecutionCommand command) {
        Optional<CardRefundRequestDetail> existing = findRefundRequestByExecutionKey(command.idempotencyKey());
        if (existing.isPresent()) return existing.get();
        CardRefundRequestSummary request = command.request().request();
        if (mapper.markCardRefundExecuted(
                request.id(), command.version(), command.idempotencyKey(), command.operatorId()) != 1) {
            throw new DuplicateResourceException("退卡申请已被他人处理，请刷新后重试");
        }
        List<MemberCardBalanceRow> balances = mapper.lockMemberCardBalances(request.memberCardId());
        List<MemberCardBalanceRow> remaining = balances.stream()
                .filter(item -> item.remainingTimes().signum() > 0).toList();
        if (remaining.isEmpty()) throw new DuplicateResourceException("次卡已无可退剩余次数");
        if (balances.stream().anyMatch(item -> item.frozenTimes().signum() > 0)) {
            throw new IllegalArgumentException("次卡存在冻结次数，不能执行退卡");
        }
        BigDecimal totalTimes = remaining.stream().map(MemberCardBalanceRow::remainingTimes)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal allocated = BigDecimal.ZERO.setScale(4);
        for (int index = 0; index < remaining.size(); index++) {
            MemberCardBalanceRow balance = remaining.get(index);
            BigDecimal value = index == remaining.size() - 1
                    ? request.refundAmount().subtract(allocated)
                    : request.refundAmount().multiply(balance.remainingTimes())
                            .divide(totalTimes, 4, RoundingMode.HALF_UP);
            value = value.setScale(4, RoundingMode.HALF_UP);
            allocated = allocated.add(value);
            if (mapper.clearCardBalance(balance.id(), balance.rowVersion()) != 1) {
                throw new DuplicateResourceException("次卡次数已发生变化，无法执行退卡");
            }
            mapper.insertCardRefundOutLedger(
                    numbers.cardLedgerNo(), request.id(), balance, value, command.operatorId());
        }
        if (mapper.markCardRefunded(request.memberCardId(), request.cardVersion(), command.operatorId()) != 1) {
            throw new DuplicateResourceException("次卡状态已发生变化，无法执行退卡");
        }
        if (request.refundAmount().signum() > 0) {
            mapper.insertCardRefundPayment(
                    numbers.cardRefundPaymentNo(), request, command.externalRefundReference(),
                    command.idempotencyKey(), command.operatorId());
        }
        return findRefundRequest(request.id()).orElseThrow();
    }

    private Optional<CardRefundRequestDetail> detail(CardRefundRequestSummary summary) {
        if (summary == null) return Optional.empty();
        return Optional.of(new CardRefundRequestDetail(
                summary, mapper.findRefundQuoteItems(summary.quoteId()), mapper.findCardRefundPayment(summary.id())));
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
