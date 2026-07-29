package com.yuezhijian.server.asset;

import com.yuezhijian.server.common.ResourceNotFoundException;
import com.yuezhijian.server.iam.AccessCatalogService;
import com.yuezhijian.server.masterdata.EmployeeSummary;
import com.yuezhijian.server.masterdata.MasterDataRepository;
import com.yuezhijian.server.member.MemberDetail;
import com.yuezhijian.server.member.MemberRepository;
import com.yuezhijian.server.trade.PaymentMethodOption;
import com.yuezhijian.server.trade.TradeRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class AssetService {
    private final AssetRepository repository;
    private final MemberRepository members;
    private final TradeRepository trades;
    private final MasterDataRepository masterData;
    private final AccessCatalogService accessCatalog;
    private final AssetNumberGenerator numbers;

    public AssetService(
            AssetRepository repository,
            MemberRepository members,
            TradeRepository trades,
            MasterDataRepository masterData,
            AccessCatalogService accessCatalog,
            AssetNumberGenerator numbers) {
        this.repository = repository;
        this.members = members;
        this.trades = trades;
        this.masterData = masterData;
        this.accessCatalog = accessCatalog;
        this.numbers = numbers;
    }

    public BalanceAccount balanceAccount(long memberId) {
        requireMember(memberId, false);
        return repository.findBalanceAccount(memberId)
                .orElseThrow(() -> new ResourceNotFoundException("会员储值账户不存在"));
    }

    public List<BalanceLedgerItem> balanceLedgers(long memberId, int limit) {
        requireMember(memberId, false);
        return repository.balanceLedgers(memberId, safeLimit(limit));
    }

    public PointAccount pointAccount(long memberId) {
        requireMember(memberId, false);
        return repository.findPointAccount(memberId)
                .orElseThrow(() -> new ResourceNotFoundException("会员积分账户不存在"));
    }

    public List<PointLedgerItem> pointLedgers(long memberId, int limit) {
        requireMember(memberId, false);
        return repository.pointLedgers(memberId, safeLimit(limit));
    }

    public RechargeQuote quoteRecharge(long memberId, RechargeQuoteRequest request, String username) {
        MemberDetail member = requireMember(memberId, true);
        BigDecimal rechargeAmount = money(request.rechargeAmount());
        BigDecimal giftAmount = money(request.giftAmount() == null ? BigDecimal.ZERO : request.giftAmount());
        if (rechargeAmount.compareTo(new BigDecimal("1000000")) > 0
                || giftAmount.compareTo(rechargeAmount.multiply(new BigDecimal("5"))) > 0) {
            throw new IllegalArgumentException("充值金额或赠送金额超出允许范围");
        }
        PaymentMethodOption method = requirePaymentMethod(member.ownerStoreId(), request.paymentMethodId());
        if ("STORED_VALUE".equals(method.type())) throw new IllegalArgumentException("充值不能使用储值余额支付");
        return repository.createRechargeQuote(new RechargeQuoteDraft(
                numbers.rechargeQuoteNo(), memberId, rechargeAmount, giftAmount, method.id(), method.name(),
                LocalDateTime.now().plusMinutes(10), currentUserId(username)));
    }

    public RechargeOrder createRecharge(long memberId, CreateRechargeRequest request, String username) {
        RechargeOrder existing = repository.findRechargeOrderByIdempotencyKey(request.idempotencyKey()).orElse(null);
        if (existing != null) {
            if (existing.memberId() != memberId) throw new IllegalArgumentException("幂等键已用于其他会员");
            return existing;
        }
        requireMember(memberId, true);
        validateStore(request.storeId());
        RechargeQuote quote = repository.findRechargeQuote(request.quoteNo())
                .orElseThrow(() -> new IllegalArgumentException("充值试算不存在"));
        if (quote.memberId() != memberId) throw new IllegalArgumentException("充值试算与会员不匹配");
        if (quote.used() || quote.expiresAt().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("充值试算已失效，请重新试算");
        }
        PaymentMethodOption method = requirePaymentMethod(request.storeId(), quote.paymentMethodId());
        String externalReference = trimToNull(request.externalReference());
        if (method.needsExternalReference() && externalReference == null) {
            throw new IllegalArgumentException(method.name() + "必须填写外部凭证号");
        }
        if (request.salesEmployeeId() != null) validateSalesEmployee(request.storeId(), request.salesEmployeeId());
        return repository.createRechargeOrder(new RechargeOrderDraft(
                numbers.rechargeNo(), quote, request.storeId(), storeName(request.storeId()),
                request.salesEmployeeId(), externalReference, request.idempotencyKey(), currentUserId(username)));
    }

    public RechargeOrder rechargeDetail(long id) {
        return repository.findRechargeOrder(id)
                .orElseThrow(() -> new ResourceNotFoundException("充值单不存在"));
    }

    public RechargeOrder confirmRecharge(long id, RechargeActionRequest request, String username) {
        return repository.confirmRecharge(id, request.version(), currentUserId(username));
    }

    public RechargeOrder cancelRecharge(long id, RechargeActionRequest request, String username) {
        String reason = trimToNull(request.reason());
        if (reason == null) throw new IllegalArgumentException("取消充值必须填写原因");
        return repository.cancelRecharge(id, request.version(), reason, currentUserId(username));
    }

    public PointAccount adjustPoints(long memberId, PointAdjustmentRequest request, String username) {
        requireMember(memberId, true);
        if (request.changePoints() == 0) throw new IllegalArgumentException("积分变动值不能为0");
        return repository.adjustPoints(new PointAdjustmentCommand(
                memberId, request.changePoints(), request.reason().trim(),
                "point-adjustment:" + memberId + ':' + request.idempotencyKey(), currentUserId(username)));
    }

    private MemberDetail requireMember(long memberId, boolean active) {
        MemberDetail member = members.findById(memberId)
                .orElseThrow(() -> new ResourceNotFoundException("会员不存在"));
        if (active && !"ACTIVE".equals(member.status())) throw new IllegalArgumentException("会员当前状态不可变更资产");
        return member;
    }

    private PaymentMethodOption requirePaymentMethod(long storeId, long paymentMethodId) {
        return trades.paymentMethods(storeId).stream().filter(item -> item.id() == paymentMethodId)
                .findFirst().orElseThrow(() -> new IllegalArgumentException("支付方式未在当前门店启用"));
    }

    private void validateSalesEmployee(long storeId, long employeeId) {
        EmployeeSummary employee = masterData.employees(storeId, null).stream()
                .filter(item -> item.id() == employeeId && item.canSell() && "ACTIVE".equals(item.status()))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("销售员工不存在或当前不可售卡/充值"));
        if (employee.storeId() != storeId) throw new IllegalArgumentException("销售员工不属于当前门店");
    }

    private void validateStore(long storeId) {
        boolean valid = accessCatalog.stores().stream()
                .anyMatch(store -> store.id() == storeId && "ACTIVE".equals(store.status()));
        if (!valid) throw new IllegalArgumentException("所选门店不存在或已停用");
    }

    private String storeName(long storeId) {
        return accessCatalog.stores().stream().filter(store -> store.id() == storeId)
                .map(store -> store.name()).findFirst().orElseThrow();
    }

    private long currentUserId(String username) { return accessCatalog.userIdentity(username).id(); }
    private int safeLimit(int limit) { return Math.min(Math.max(limit, 1), 200); }
    private BigDecimal money(BigDecimal amount) { return amount.setScale(4, RoundingMode.HALF_UP); }
    private String trimToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
}
