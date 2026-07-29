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
