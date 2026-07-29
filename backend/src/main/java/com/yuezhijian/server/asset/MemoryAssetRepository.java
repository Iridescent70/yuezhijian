package com.yuezhijian.server.asset;

import com.yuezhijian.server.common.DuplicateResourceException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("memory")
public class MemoryAssetRepository implements AssetRepository {
    private final Map<Long, MutableBalanceAccount> balances = new LinkedHashMap<>();
    private final Map<Long, MutablePointAccount> points = new LinkedHashMap<>();
    private final List<BalanceLedgerItem> balanceLedgers = new ArrayList<>();
    private final List<PointLedgerItem> pointLedgers = new ArrayList<>();
    private final Map<String, RechargeQuote> quotes = new LinkedHashMap<>();
    private final Map<Long, RechargeOrder> recharges = new LinkedHashMap<>();
    private final Map<String, Long> rechargeIdempotency = new LinkedHashMap<>();
    private final Map<String, Long> pointCorrelations = new LinkedHashMap<>();
    private final AtomicLong quoteIds = new AtomicLong(1000);
    private final AtomicLong rechargeIds = new AtomicLong(2000);
    private final AtomicLong balanceLedgerIds = new AtomicLong(3000);
    private final AtomicLong pointLedgerIds = new AtomicLong(4000);
    private final AssetNumberGenerator numbers;

    public MemoryAssetRepository(AssetNumberGenerator numbers) {
        this.numbers = numbers;
        balances.put(1001L, new MutableBalanceAccount(
                new BigDecimal("1280.0000"), BigDecimal.ZERO.setScale(4), new BigDecimal("3000.0000"), null, 1));
        balances.put(1002L, new MutableBalanceAccount(
                new BigDecimal("320.0000"), BigDecimal.ZERO.setScale(4), new BigDecimal("800.0000"), null, 1));
        balances.put(1003L, new MutableBalanceAccount(
                BigDecimal.ZERO.setScale(4), BigDecimal.ZERO.setScale(4), BigDecimal.ZERO.setScale(4), null, 1));
        points.put(1001L, new MutablePointAccount(860, 1260, null, 1));
        points.put(1002L, new MutablePointAccount(120, 320, null, 1));
        points.put(1003L, new MutablePointAccount(0, 0, null, 1));
    }

    @Override
    public synchronized Optional<BalanceAccount> findBalanceAccount(long memberId) {
        return Optional.of(toBalance(memberId, balance(memberId)));
    }

    @Override
    public synchronized List<BalanceLedgerItem> balanceLedgers(long memberId, int limit) {
        return balanceLedgers.stream()
                .filter(item -> item.memberId() == memberId)
                .sorted(Comparator.comparing(BalanceLedgerItem::occurredAt).reversed()
                        .thenComparing(BalanceLedgerItem::id, Comparator.reverseOrder()))
                .limit(limit)
                .toList();
    }

    @Override
    public synchronized Optional<PointAccount> findPointAccount(long memberId) {
        return Optional.of(toPoint(memberId, point(memberId)));
    }

    @Override
    public synchronized List<PointLedgerItem> pointLedgers(long memberId, int limit) {
        return pointLedgers.stream()
                .filter(item -> item.memberId() == memberId)
                .sorted(Comparator.comparing(PointLedgerItem::occurredAt).reversed()
                        .thenComparing(PointLedgerItem::id, Comparator.reverseOrder()))
                .limit(limit)
                .toList();
    }

    @Override
    public synchronized RechargeQuote createRechargeQuote(RechargeQuoteDraft draft) {
        RechargeQuote quote = new RechargeQuote(
                quoteIds.incrementAndGet(), draft.quoteNo(), draft.memberId(), draft.rechargeAmount(),
                draft.giftAmount(), draft.rechargeAmount().add(draft.giftAmount()), draft.paymentMethodId(),
                draft.paymentMethodName(), draft.expiresAt(), false);
        quotes.put(quote.quoteNo(), quote);
        return quote;
    }

    @Override
    public synchronized Optional<RechargeQuote> findRechargeQuote(String quoteNo) {
        return Optional.ofNullable(quotes.get(quoteNo));
    }

    @Override
    public synchronized Optional<RechargeOrder> findRechargeOrder(long id) {
        return Optional.ofNullable(recharges.get(id));
    }

    @Override
    public synchronized Optional<RechargeOrder> findRechargeOrderByIdempotencyKey(String key) {
        Long id = rechargeIdempotency.get(key);
        return id == null ? Optional.empty() : findRechargeOrder(id);
    }

    @Override
    public synchronized RechargeOrder createRechargeOrder(RechargeOrderDraft draft) {
        Optional<RechargeOrder> existing = findRechargeOrderByIdempotencyKey(draft.idempotencyKey());
        if (existing.isPresent()) return existing.get();
        RechargeQuote quote = quotes.get(draft.quote().quoteNo());
        if (quote == null || quote.used() || quote.expiresAt().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("充值试算已失效，请重新试算");
        }
        long id = rechargeIds.incrementAndGet();
        RechargeOrder order = new RechargeOrder(
                id, draft.rechargeNo(), quote.quoteNo(), quote.memberId(), draft.storeId(), draft.storeName(),
                quote.rechargeAmount(), quote.giftAmount(), quote.creditAmount(), quote.paymentMethodId(),
                quote.paymentMethodName(), draft.externalReference(), draft.salesEmployeeId(), "PENDING_CONFIRM",
                null, null, null, LocalDateTime.now(), "1");
        recharges.put(id, order);
        rechargeIdempotency.put(draft.idempotencyKey(), id);
        quotes.put(quote.quoteNo(), copyQuote(quote, true));
        return order;
    }

    @Override
    public synchronized RechargeOrder confirmRecharge(long id, String version, long operatorId) {
        RechargeOrder order = requireRecharge(id);
        if ("CONFIRMED".equals(order.status())) return order;
        requirePendingVersion(order, version);
        MutableBalanceAccount account = balance(order.memberId());
        LocalDateTime occurredAt = LocalDateTime.now();
        BigDecimal before = account.available;
        BigDecimal paidAfter = before.add(order.rechargeAmount());
        addBalanceLedger(order, "RECHARGE", before, order.rechargeAmount(), paidAfter,
                "recharge:" + order.id() + ":paid", occurredAt, "充值本金");
        BigDecimal after = paidAfter;
        if (order.giftAmount().signum() > 0) {
            after = paidAfter.add(order.giftAmount());
            addBalanceLedger(order, "RECHARGE_GIFT", paidAfter, order.giftAmount(), after,
                    "recharge:" + order.id() + ":gift", occurredAt, "充值赠送");
        }
        account.available = after;
        account.totalRecharged = account.totalRecharged.add(order.rechargeAmount());
        account.lastTransactionAt = occurredAt;
        account.version++;
        RechargeOrder confirmed = copyOrder(order, "CONFIRMED", occurredAt, null, null);
        recharges.put(id, confirmed);
        return confirmed;
    }

    @Override
    public synchronized RechargeOrder cancelRecharge(
            long id, String version, String reason, long operatorId) {
        RechargeOrder order = requireRecharge(id);
        if ("CANCELLED".equals(order.status())) return order;
        requirePendingVersion(order, version);
        RechargeOrder cancelled = copyOrder(order, "CANCELLED", null, LocalDateTime.now(), reason);
        recharges.put(id, cancelled);
        return cancelled;
    }

    @Override
    public synchronized PointAccount adjustPoints(PointAdjustmentCommand command) {
        if (pointCorrelations.containsKey(command.correlationId())) {
            return toPoint(command.memberId(), point(command.memberId()));
        }
        MutablePointAccount account = point(command.memberId());
        int after;
        try {
            after = Math.addExact(account.available, command.changePoints());
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("积分变动超出允许范围");
        }
        if (after < 0) throw new IllegalArgumentException("可用积分不足");
        LocalDateTime now = LocalDateTime.now();
        long id = pointLedgerIds.incrementAndGet();
        String type = command.changePoints() > 0 ? "ADJUST_IN" : "ADJUST_OUT";
        pointLedgers.add(new PointLedgerItem(
                id, command.memberId(), numbers.pointLedgerNo(), type, account.available, command.changePoints(), after,
                "MANUAL_ADJUSTMENT", command.operatorId(), null, now,
                command.correlationId(), null, command.reason()));
        account.available = after;
        if (command.changePoints() > 0) account.lifetime += command.changePoints();
        account.lastTransactionAt = now;
        account.version++;
        pointCorrelations.put(command.correlationId(), id);
        return toPoint(command.memberId(), account);
    }

    private MutableBalanceAccount balance(long memberId) {
        return balances.computeIfAbsent(memberId, ignored -> new MutableBalanceAccount(
                BigDecimal.ZERO.setScale(4), BigDecimal.ZERO.setScale(4), BigDecimal.ZERO.setScale(4), null, 1));
    }

    private MutablePointAccount point(long memberId) {
        return points.computeIfAbsent(memberId, ignored -> new MutablePointAccount(0, 0, null, 1));
    }

    private void addBalanceLedger(
            RechargeOrder order, String type, BigDecimal before, BigDecimal change, BigDecimal after,
            String correlationId, LocalDateTime occurredAt, String note) {
        balanceLedgers.add(new BalanceLedgerItem(
                balanceLedgerIds.incrementAndGet(), order.memberId(), numbers.balanceLedgerNo(), type, before, change, after,
                "RECHARGE", order.id(), order.storeId(), order.storeName(),
                occurredAt, correlationId, null, note));
    }

    private RechargeOrder requireRecharge(long id) {
        RechargeOrder order = recharges.get(id);
        if (order == null) throw new IllegalArgumentException("充值单不存在");
        return order;
    }

    private void requirePendingVersion(RechargeOrder order, String version) {
        if (!"PENDING_CONFIRM".equals(order.status())) throw new IllegalArgumentException("当前充值单状态不可操作");
        if (!order.version().equals(version)) {
            throw new DuplicateResourceException("充值单已被他人处理，请刷新后重试");
        }
    }

    private BalanceAccount toBalance(long memberId, MutableBalanceAccount account) {
        return new BalanceAccount(memberId, account.available, account.frozen, account.totalRecharged,
                account.lastTransactionAt, Integer.toString(account.version));
    }

    private PointAccount toPoint(long memberId, MutablePointAccount account) {
        return new PointAccount(memberId, account.available, account.lifetime,
                account.lastTransactionAt, Integer.toString(account.version));
    }

    private RechargeQuote copyQuote(RechargeQuote quote, boolean used) {
        return new RechargeQuote(
                quote.id(), quote.quoteNo(), quote.memberId(), quote.rechargeAmount(), quote.giftAmount(),
                quote.creditAmount(), quote.paymentMethodId(), quote.paymentMethodName(), quote.expiresAt(), used);
    }

    private RechargeOrder copyOrder(
            RechargeOrder order, String status, LocalDateTime confirmedAt,
            LocalDateTime cancelledAt, String cancelReason) {
        return new RechargeOrder(
                order.id(), order.rechargeNo(), order.quoteNo(), order.memberId(), order.storeId(), order.storeName(),
                order.rechargeAmount(), order.giftAmount(), order.creditAmount(), order.paymentMethodId(),
                order.paymentMethodName(), order.externalReference(), order.salesEmployeeId(), status,
                confirmedAt, cancelledAt, cancelReason, order.createdAt(),
                Long.toString(Long.parseLong(order.version()) + 1));
    }

    private static final class MutableBalanceAccount {
        private BigDecimal available;
        private final BigDecimal frozen;
        private BigDecimal totalRecharged;
        private LocalDateTime lastTransactionAt;
        private int version;

        private MutableBalanceAccount(
                BigDecimal available, BigDecimal frozen, BigDecimal totalRecharged,
                LocalDateTime lastTransactionAt, int version) {
            this.available = available;
            this.frozen = frozen;
            this.totalRecharged = totalRecharged;
            this.lastTransactionAt = lastTransactionAt;
            this.version = version;
        }
    }

    private static final class MutablePointAccount {
        private int available;
        private int lifetime;
        private LocalDateTime lastTransactionAt;
        private int version;

        private MutablePointAccount(int available, int lifetime, LocalDateTime lastTransactionAt, int version) {
            this.available = available;
            this.lifetime = lifetime;
            this.lastTransactionAt = lastTransactionAt;
            this.version = version;
        }
    }
}
