package com.yuezhijian.server.asset;

import com.yuezhijian.server.common.DuplicateResourceException;
import com.yuezhijian.server.settings.SystemSettingsService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Profile("sqlserver")
public class SqlServerAssetRepository implements AssetRepository {
    private final AssetMapper mapper;
    private final AssetNumberGenerator numbers;
    private final SystemSettingsService settings;

    public SqlServerAssetRepository(
            AssetMapper mapper, AssetNumberGenerator numbers, SystemSettingsService settings) {
        this.mapper = mapper;
        this.numbers = numbers;
        this.settings = settings;
    }

    @Override
    public Optional<BalanceAccount> findBalanceAccount(long memberId) {
        return Optional.ofNullable(mapper.findBalanceAccount(memberId)).map(this::toBalanceAccount);
    }

    @Override
    public List<BalanceLedgerItem> balanceLedgers(long memberId, int limit) {
        return mapper.findBalanceLedgers(memberId, limit);
    }

    @Override
    public Optional<PointAccount> findPointAccount(long memberId) {
        return Optional.ofNullable(mapper.findPointAccount(memberId)).map(this::toPointAccount);
    }

    @Override
    public List<PointLedgerItem> pointLedgers(long memberId, int limit) {
        return mapper.findPointLedgers(memberId, limit);
    }

    @Override
    @Transactional
    public RechargeQuote createRechargeQuote(RechargeQuoteDraft draft) {
        mapper.insertRechargeQuote(draft);
        return findRechargeQuote(draft.quoteNo())
                .orElseThrow(() -> new IllegalArgumentException("会员储值账户不存在"));
    }

    @Override
    public Optional<RechargeQuote> findRechargeQuote(String quoteNo) {
        return Optional.ofNullable(mapper.findRechargeQuote(quoteNo));
    }

    @Override
    public Optional<RechargeOrder> findRechargeOrder(long id) {
        return Optional.ofNullable(mapper.findRechargeOrder(id)).map(this::toRechargeOrder);
    }

    @Override
    public Optional<RechargeOrder> findRechargeOrderByIdempotencyKey(String idempotencyKey) {
        return Optional.ofNullable(mapper.findRechargeOrderByIdempotencyKey(idempotencyKey))
                .map(this::toRechargeOrder);
    }

    @Override
    @Transactional
    public RechargeOrder createRechargeOrder(RechargeOrderDraft draft) {
        Optional<RechargeOrder> existing = findRechargeOrderByIdempotencyKey(draft.idempotencyKey());
        if (existing.isPresent()) return existing.get();
        if (mapper.markRechargeQuoteUsed(draft.quote().id()) != 1) {
            throw new DuplicateResourceException("充值试算已失效或已被使用，请重新试算");
        }
        long id = mapper.insertRechargeOrder(draft);
        return requireRecharge(id);
    }

    @Override
    @Transactional
    public RechargeOrder confirmRecharge(long id, String version, long operatorId) {
        RechargeOrderRow current = requireRechargeRow(id);
        if ("CONFIRMED".equals(current.status())) return toRechargeOrder(current);
        requirePendingVersion(current, version);
        BalanceAccountRow account = mapper.lockBalanceAccount(current.memberId());
        if (account == null) throw new IllegalArgumentException("会员储值账户不存在");
        LocalDateTime now = LocalDateTime.now();
        BigDecimal before = account.availableBalance();
        BigDecimal paidAfter = before.add(current.rechargeAmount());
        BigDecimal after = paidAfter.add(current.giftAmount());
        if (mapper.creditBalance(
                account.accountId(), current.creditAmount(), current.rechargeAmount(), now, account.rowVersion()) != 1) {
            throw new DuplicateResourceException("储值账户已发生变化，请重新确认");
        }
        mapper.insertBalanceLedger(
                numbers.balanceLedgerNo(), account.accountId(), "RECHARGE", before, current.rechargeAmount(),
                paidAfter, current.id(), current.storeId(), now, "recharge:" + current.id() + ":paid",
                "充值本金", operatorId);
        if (current.giftAmount().signum() > 0) {
            mapper.insertBalanceLedger(
                    numbers.balanceLedgerNo(), account.accountId(), "RECHARGE_GIFT", paidAfter,
                    current.giftAmount(), after, current.id(), current.storeId(), now,
                    "recharge:" + current.id() + ":gift", "充值赠送", operatorId);
        }
        if (mapper.confirmRecharge(id, current.rowVersion(), now, operatorId) != 1) {
            throw new DuplicateResourceException("充值单已被他人处理，请刷新后重试");
        }
        return requireRecharge(id);
    }

    @Override
    @Transactional
    public RechargeOrder cancelRecharge(long id, String version, String reason, long operatorId) {
        RechargeOrderRow current = requireRechargeRow(id);
        if ("CANCELLED".equals(current.status())) return toRechargeOrder(current);
        requirePendingVersion(current, version);
        if (mapper.cancelRecharge(id, current.rowVersion(), reason, operatorId) != 1) {
            throw new DuplicateResourceException("充值单已被他人处理，请刷新后重试");
        }
        return requireRecharge(id);
    }

    @Override
    @Transactional
    public PointAccount adjustPoints(PointAdjustmentCommand command) {
        if (mapper.countPointLedgerByCorrelation(command.correlationId()) > 0) {
            return requirePointAccount(command.memberId());
        }
        PointAccountRow account = mapper.lockPointAccount(command.memberId());
        if (account == null) throw new IllegalArgumentException("会员积分账户不存在");
        int after;
        try {
            after = Math.addExact(account.availablePoints(), command.changePoints());
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("积分变动超出允许范围");
        }
        if (after < 0) throw new IllegalArgumentException("可用积分不足");
        LocalDateTime now = LocalDateTime.now();
        if (mapper.adjustPoints(
                account.accountId(), command.changePoints(), now, account.rowVersion()) != 1) {
            throw new DuplicateResourceException("积分账户已发生变化，请刷新后重试");
        }
        mapper.insertPointLedger(
                numbers.pointLedgerNo(), account.accountId(), command.changePoints() > 0 ? "ADJUST_IN" : "ADJUST_OUT",
                account.availablePoints(), command.changePoints(), after, now, command.correlationId(),
                command.reason(), command.operatorId());
        return requirePointAccount(command.memberId());
    }

    @Override
    public int pointsPerYuan() {
        return settings.integerValue("ASSET", "POINTS_PER_YUAN", 100, 1, 100000);
    }

    @Override
    @Transactional
    public void consumeBalance(BalanceSettlementConsumption command) {
        BalanceAccountRow account = mapper.lockBalanceAccount(command.memberId());
        if (account == null) throw new IllegalArgumentException("会员储值账户不存在");
        requireVersion(account.rowVersion(), command.accountVersion(), "储值余额已发生变化，请重新试算");
        if (account.availableBalance().compareTo(command.amount()) < 0) throw new IllegalArgumentException("可用储值余额不足");
        LocalDateTime now = LocalDateTime.now();
        BigDecimal after = account.availableBalance().subtract(command.amount());
        if (mapper.consumeBalance(account.accountId(), command.amount(), now, account.rowVersion()) != 1) {
            throw new DuplicateResourceException("储值余额已发生变化，请重新试算");
        }
        long ledgerId = mapper.insertBalanceConsumeLedger(
                numbers.balanceLedgerNo(), account.accountId(), account.availableBalance(), command.amount(), after,
                command.billId(), command.storeId(), now, command.displayName(), command.operatorId());
        mapper.insertAccountAssetUsage(
                command.billId(), "BALANCE", command.memberId(), command.amount(), command.amount(),
                ledgerId, command.displayName(), command.operatorId());
    }

    @Override
    @Transactional
    public void consumePoints(PointSettlementConsumption command) {
        PointAccountRow account = mapper.lockPointAccount(command.memberId());
        if (account == null) throw new IllegalArgumentException("会员积分账户不存在");
        requireVersion(account.rowVersion(), command.accountVersion(), "积分余额已发生变化，请重新试算");
        if (account.availablePoints() < command.points()) throw new IllegalArgumentException("可用积分不足");
        LocalDateTime now = LocalDateTime.now();
        int after = account.availablePoints() - command.points();
        if (mapper.consumePoints(account.accountId(), command.points(), now, account.rowVersion()) != 1) {
            throw new DuplicateResourceException("积分余额已发生变化，请重新试算");
        }
        long ledgerId = mapper.insertPointConsumeLedger(
                numbers.pointLedgerNo(), account.accountId(), account.availablePoints(), command.points(), after,
                command.billId(), now, command.displayName(), command.operatorId());
        mapper.insertAccountAssetUsage(
                command.billId(), "POINT", command.memberId(), BigDecimal.valueOf(command.points()), command.amount(),
                ledgerId, command.displayName(), command.operatorId());
    }

    @Override
    @Transactional
    public void refundBalance(BalanceRefundCommand command) {
        if (command.amount() == null || command.amount().signum() <= 0 || command.originalLedgerId() == null) {
            throw new IllegalArgumentException("储值冲销数据不完整");
        }
        BalanceAccountRow account = mapper.lockBalanceAccount(command.memberId());
        if (account == null) throw new IllegalArgumentException("会员储值账户不存在");
        LocalDateTime now = LocalDateTime.now();
        BigDecimal after = account.availableBalance().add(command.amount());
        if (mapper.refundBalance(account.accountId(), command.amount(), now, account.rowVersion()) != 1) {
            throw new DuplicateResourceException("储值余额已发生变化，请重新执行冲销");
        }
        mapper.insertBalanceRefundLedger(
                numbers.balanceLedgerNo(), account.accountId(), account.availableBalance(), command.amount(),
                after, now, command);
    }

    @Override
    @Transactional
    public void refundPoints(PointRefundCommand command) {
        if (command.points() <= 0 || command.originalLedgerId() == null) {
            throw new IllegalArgumentException("积分冲销数据不完整");
        }
        PointAccountRow account = mapper.lockPointAccount(command.memberId());
        if (account == null) throw new IllegalArgumentException("会员积分账户不存在");
        int after;
        try {
            after = Math.addExact(account.availablePoints(), command.points());
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("积分返还超出允许范围");
        }
        LocalDateTime now = LocalDateTime.now();
        if (mapper.refundPoints(account.accountId(), command.points(), now, account.rowVersion()) != 1) {
            throw new DuplicateResourceException("积分余额已发生变化，请重新执行冲销");
        }
        mapper.insertPointRefundLedger(
                numbers.pointLedgerNo(), account.accountId(), account.availablePoints(), command.points(),
                after, now, command);
    }

    private BalanceAccount toBalanceAccount(BalanceAccountRow row) {
        return new BalanceAccount(
                row.memberId(), row.availableBalance(), row.frozenBalance(), row.totalRecharged(),
                row.lastTransactionAt(), encode(row.rowVersion()));
    }

    private PointAccount toPointAccount(PointAccountRow row) {
        return new PointAccount(
                row.memberId(), row.availablePoints(), row.lifetimePoints(),
                row.lastTransactionAt(), encode(row.rowVersion()));
    }

    private RechargeOrder toRechargeOrder(RechargeOrderRow row) {
        return new RechargeOrder(
                row.id(), row.rechargeNo(), row.quoteNo(), row.memberId(), row.storeId(), row.storeName(),
                row.rechargeAmount(), row.giftAmount(), row.creditAmount(), row.paymentMethodId(),
                row.paymentMethodName(), row.externalReference(), row.salesEmployeeId(), row.status(),
                row.confirmedAt(), row.cancelledAt(), row.cancelReason(), row.createdAt(), encode(row.rowVersion()));
    }

    private RechargeOrder requireRecharge(long id) {
        return findRechargeOrder(id).orElseThrow(() -> new IllegalArgumentException("充值单不存在"));
    }

    private RechargeOrderRow requireRechargeRow(long id) {
        RechargeOrderRow row = mapper.findRechargeOrder(id);
        if (row == null) throw new IllegalArgumentException("充值单不存在");
        return row;
    }

    private PointAccount requirePointAccount(long memberId) {
        return findPointAccount(memberId).orElseThrow(() -> new IllegalArgumentException("会员积分账户不存在"));
    }

    private void requirePendingVersion(RechargeOrderRow row, String version) {
        if (!"PENDING_CONFIRM".equals(row.status())) throw new IllegalArgumentException("当前充值单状态不可操作");
        byte[] expected;
        try {
            expected = Base64.getDecoder().decode(version);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("充值单版本格式不正确");
        }
        if (!Arrays.equals(expected, row.rowVersion())) {
            throw new DuplicateResourceException("充值单已被他人处理，请刷新后重试");
        }
    }

    private String encode(byte[] version) { return Base64.getEncoder().encodeToString(version); }

    private void requireVersion(byte[] current, String expected, String message) {
        byte[] decoded;
        try { decoded = Base64.getDecoder().decode(expected); }
        catch (IllegalArgumentException exception) { throw new IllegalArgumentException("资产版本格式不正确"); }
        if (!Arrays.equals(current, decoded)) throw new DuplicateResourceException(message);
    }
}
