package com.yuezhijian.server.trade;

import com.yuezhijian.server.asset.AssetRepository;
import com.yuezhijian.server.asset.BalanceRefundCommand;
import com.yuezhijian.server.asset.CardRefundCommand;
import com.yuezhijian.server.asset.CardRepository;
import com.yuezhijian.server.asset.PointRefundCommand;
import com.yuezhijian.server.common.DuplicateResourceException;
import com.yuezhijian.server.common.ResourceNotFoundException;
import com.yuezhijian.server.commission.CommissionService;
import com.yuezhijian.server.benefit.BenefitRepository;
import com.yuezhijian.server.benefit.VoucherRefundCommand;
import com.yuezhijian.server.iam.AccessCatalogService;
import com.yuezhijian.server.visit.VisitService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReversalService {
    private static final Set<String> STATUSES = Set.of("SUBMITTED", "APPROVED", "REJECTED", "EXECUTED");

    private final TradeRepository trades;
    private final AssetRepository assets;
    private final CardRepository cards;
    private final BenefitRepository benefits;
    private final CommissionService commissions;
    private final VisitService visits;
    private final AccessCatalogService accessCatalog;
    private final TradeNumberGenerator numbers;

    public ReversalService(
            TradeRepository trades,
            AssetRepository assets,
            CardRepository cards,
            BenefitRepository benefits,
            CommissionService commissions,
            VisitService visits,
            AccessCatalogService accessCatalog,
            TradeNumberGenerator numbers) {
        this.trades = trades;
        this.assets = assets;
        this.cards = cards;
        this.benefits = benefits;
        this.commissions = commissions;
        this.visits = visits;
        this.accessCatalog = accessCatalog;
        this.numbers = numbers;
    }

    public List<ReversalSummary> search(String status) {
        return trades.reversals(normalizeStatus(status));
    }

    public ReversalDetail detail(long id) {
        return trades.findReversal(id)
                .orElseThrow(() -> new ResourceNotFoundException("冲销申请不存在"));
    }

    @Transactional
    public ReversalDetail create(long billId, CreateReversalRequest request, String username) {
        String key = request.idempotencyKey().trim();
        Optional<ReversalDetail> existing = trades.findReversalByRequestKey(key);
        if (existing.isPresent()) return existing.get();
        BillDetail bill = trades.findById(billId)
                .orElseThrow(() -> new ResourceNotFoundException("账单不存在"));
        if (!BillStatus.SETTLED.name().equals(bill.bill().status())) {
            throw new IllegalArgumentException("只有已结算账单可以申请冲销");
        }
        if (bill.bill().receivableAmount().signum() <= 0) {
            throw new IllegalArgumentException("账单退款金额必须大于0");
        }
        if (trades.findActiveReversalByBill(billId).isPresent()) {
            throw new DuplicateResourceException("当前账单已有待处理或已执行的冲销记录");
        }
        return trades.createReversal(new ReversalDraft(
                numbers.reversalNo(), bill, request.reason().trim(), key, currentUserId(username)));
    }

    @Transactional
    public ReversalDetail review(long id, ReviewReversalRequest request, String username) {
        ReversalDetail current = detail(id);
        if (!"SUBMITTED".equals(current.reversal().status())) {
            throw new IllegalArgumentException("只有待审批的冲销申请可以审核");
        }
        String comment = trimToNull(request.comment());
        if (!request.approved() && comment == null) throw new IllegalArgumentException("驳回时必须填写原因");
        return trades.reviewReversal(
                id, request.approved(), comment, request.version(), currentUserId(username));
    }

    @Transactional
    public ReversalDetail execute(long id, ExecuteReversalRequest request, String username) {
        String key = request.idempotencyKey().trim();
        Optional<ReversalDetail> existing = trades.findReversalByExecutionKey(key);
        if (existing.isPresent()) return existing.get();
        ReversalDetail reversal = detail(id);
        if (!"APPROVED".equals(reversal.reversal().status())) {
            throw new IllegalArgumentException("冲销申请审批通过后才能执行");
        }
        if (!reversal.reversal().version().equals(request.version())) {
            throw new DuplicateResourceException("冲销申请已被他人处理，请刷新后重试");
        }
        BillDetail bill = trades.findById(reversal.reversal().billId())
                .orElseThrow(() -> new ResourceNotFoundException("账单不存在"));
        if (!BillStatus.SETTLED.name().equals(bill.bill().status())) {
            throw new DuplicateResourceException("账单状态已发生变化，无法执行冲销");
        }
        validateImpacts(reversal, bill);
        long operatorId = currentUserId(username);
        for (ReversalAssetImpact impact : reversal.assets()) {
            String note = "整单冲销：" + reversal.reversal().reversalNo() + "，" + impact.displayName();
            switch (impact.assetType()) {
                case "BALANCE" -> assets.refundBalance(new BalanceRefundCommand(
                        id, impact.usageId(), impact.memberId(), bill.bill().storeId(), impact.amount(),
                        impact.assetLedgerId(), note, operatorId));
                case "POINT" -> assets.refundPoints(new PointRefundCommand(
                        id, impact.usageId(), impact.memberId(), impact.quantity().intValueExact(),
                        impact.assetLedgerId(), note, operatorId));
                case "CARD" -> cards.refundCard(new CardRefundCommand(
                        id, impact.usageId(), impact.memberId(), impact.memberCardId(),
                        impact.memberCardBalanceId(), impact.serviceId(), impact.quantity(), impact.amount(),
                        impact.assetLedgerId(), note, operatorId));
                case "VOUCHER" -> benefits.refund(new VoucherRefundCommand(
                        id, bill.bill().id(), impact.usageId(), impact.voucherCodeId(),
                        impact.assetLedgerId(), note, operatorId));
                default -> throw new IllegalArgumentException("不支持的冲销资产类型：" + impact.assetType());
            }
        }
        ReversalDetail executed = trades.executeReversal(new ReversalExecutionCommand(
                reversal, request.version(), key, operatorId));
        commissions.reverseBill(bill, executed, operatorId);
        visits.cancelPendingByBill(bill.bill().id(), "账单已整单冲销：" + executed.reversal().reversalNo(), operatorId);
        return executed;
    }

    private void validateImpacts(ReversalDetail reversal, BillDetail bill) {
        if (reversal.reversal().refundAmount().compareTo(bill.bill().receivableAmount()) != 0) {
            throw new DuplicateResourceException("冲销金额与账单应收不一致");
        }
        BigDecimal external = reversal.payments().stream().map(ReversalPaymentImpact::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal assetAmount = reversal.assets().stream().map(ReversalAssetImpact::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (external.add(assetAmount).compareTo(reversal.reversal().refundAmount()) != 0) {
            throw new IllegalArgumentException("冲销支付与会员资产金额合计不等于账单金额");
        }
        for (ReversalPaymentImpact payment : reversal.payments()) {
            if (!"SUCCESS".equals(payment.status())) throw new IllegalArgumentException("存在不可退款的支付记录");
        }
        for (ReversalAssetImpact impact : reversal.assets()) {
            if (bill.bill().memberId() == null || bill.bill().memberId() != impact.memberId()) {
                throw new IllegalArgumentException("冲销资产与账单会员不匹配");
            }
            if (impact.assetLedgerId() == null || impact.quantity() == null || impact.quantity().signum() <= 0
                    || impact.amount() == null || impact.amount().signum() <= 0) {
                throw new IllegalArgumentException("会员资产冲销数据不完整");
            }
            if ("POINT".equals(impact.assetType())) impact.quantity().intValueExact();
            if ("CARD".equals(impact.assetType()) && (impact.memberCardId() == null
                    || impact.memberCardBalanceId() == null || impact.serviceId() == null)) {
                throw new IllegalArgumentException("次卡冲销数据不完整");
            }
            if ("VOUCHER".equals(impact.assetType()) && impact.voucherCodeId() == null) {
                throw new IllegalArgumentException("代金券冲销数据不完整");
            }
        }
    }

    private String normalizeStatus(String status) {
        String value = trimToNull(status);
        if (value == null) return null;
        String normalized = value.toUpperCase(Locale.ROOT);
        if (!STATUSES.contains(normalized)) throw new IllegalArgumentException("冲销状态无效");
        return normalized;
    }

    private long currentUserId(String username) { return accessCatalog.userIdentity(username).id(); }

    private String trimToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
}
