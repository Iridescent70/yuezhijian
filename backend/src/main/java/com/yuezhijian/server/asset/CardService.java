package com.yuezhijian.server.asset;

import com.yuezhijian.server.common.DuplicateResourceException;
import com.yuezhijian.server.common.ResourceNotFoundException;
import com.yuezhijian.server.iam.AccessCatalogService;
import com.yuezhijian.server.masterdata.EmployeeSummary;
import com.yuezhijian.server.masterdata.MasterDataRepository;
import com.yuezhijian.server.masterdata.ServiceItemSummary;
import com.yuezhijian.server.member.MemberDetail;
import com.yuezhijian.server.member.MemberRepository;
import com.yuezhijian.server.trade.PaymentMethodOption;
import com.yuezhijian.server.trade.TradeRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class CardService {
    private final CardRepository repository;
    private final MemberRepository members;
    private final MasterDataRepository masterData;
    private final TradeRepository trades;
    private final AccessCatalogService accessCatalog;
    private final AssetNumberGenerator numbers;

    public CardService(
            CardRepository repository,
            MemberRepository members,
            MasterDataRepository masterData,
            TradeRepository trades,
            AccessCatalogService accessCatalog,
            AssetNumberGenerator numbers) {
        this.repository = repository;
        this.members = members;
        this.masterData = masterData;
        this.trades = trades;
        this.accessCatalog = accessCatalog;
        this.numbers = numbers;
    }

    public List<CardTypeDetail> cardTypes(Long storeId, String keyword, String status) {
        if (storeId != null) validateStore(storeId);
        String normalizedStatus = normalizeStatus(status, Set.of("ACTIVE", "DISABLED"), "次卡类型状态不正确");
        return repository.searchCardTypes(storeId, trimToNull(keyword), normalizedStatus);
    }

    public CardTypeDetail cardType(long id) {
        return repository.findCardType(id).orElseThrow(() -> new ResourceNotFoundException("次卡类型不存在"));
    }

    public CardTypeDetail createCardType(CreateCardTypeRequest request, String username) {
        String code = request.code().trim().toUpperCase(Locale.ROOT);
        if (!code.matches("[A-Z0-9][A-Z0-9_-]{1,63}")) throw new IllegalArgumentException("次卡类型编码格式不正确");
        if (repository.existsCardTypeCode(code)) throw new DuplicateResourceException("次卡类型编码已存在");
        BigDecimal salePrice = money(request.salePrice());
        BigDecimal listPrice = money(request.listPrice());
        BigDecimal totalTimes = times(request.totalTimes());
        if (listPrice.compareTo(salePrice) < 0) throw new IllegalArgumentException("次卡原价不能低于销售价");
        List<Long> storeIds = distinct(request.storeIds(), "适用门店不能重复");
        storeIds.forEach(this::validateStore);
        List<CardServiceRule> rules = validateRules(request.serviceRules(), storeIds);
        BigDecimal ruleTotal = rules.stream().map(CardServiceRule::includedTimes)
                .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(4, RoundingMode.HALF_UP);
        if (ruleTotal.compareTo(totalTimes) != 0) {
            throw new IllegalArgumentException("各项目包含次数合计必须等于次卡总次数");
        }
        return repository.createCardType(new CardTypeDraft(
                code, request.name().trim(), salePrice, listPrice, totalTimes, request.validDays(),
                money(request.purchaseThreshold() == null ? BigDecimal.ZERO : request.purchaseThreshold()),
                trimToNull(request.instructions()), request.autoRemindDays(), storeIds, rules,
                currentUserId(username)));
    }

    public List<MemberCardSummary> memberCards(long memberId, String status) {
        requireMember(memberId, false);
        String normalizedStatus = normalizeStatus(status, Set.of(
                "ACTIVE", "EXHAUSTED", "EXPIRED", "FROZEN", "TRANSFERRED", "EXCHANGED", "REFUNDED"),
                "会员次卡状态不正确");
        return repository.memberCards(memberId, normalizedStatus);
    }

    public MemberCardDetail memberCard(long id) {
        return repository.findMemberCard(id).orElseThrow(() -> new ResourceNotFoundException("会员次卡不存在"));
    }

    public CardSaleResult purchase(long memberId, PurchaseMemberCardRequest request, String username) {
        CardSaleResult existing = repository.findSaleByIdempotencyKey(request.idempotencyKey()).orElse(null);
        if (existing != null) {
            if (existing.cards().stream().anyMatch(card -> card.memberId() != memberId)) {
                throw new IllegalArgumentException("幂等键已用于其他会员");
            }
            return existing;
        }
        requireMember(memberId, true);
        validateStore(request.storeId());
        CardTypeDetail cardType = cardType(request.cardTypeId());
        if (!"ACTIVE".equals(cardType.status()) || !cardType.storeIds().contains(request.storeId())) {
            throw new IllegalArgumentException("次卡类型未在当前门店上架");
        }
        PaymentMethodOption paymentMethod = trades.paymentMethods(request.storeId()).stream()
                .filter(item -> item.id() == request.paymentMethodId())
                .findFirst().orElseThrow(() -> new IllegalArgumentException("支付方式未在当前门店启用"));
        if ("STORED_VALUE".equals(paymentMethod.type())) throw new IllegalArgumentException("当前售卡暂不支持储值余额支付");
        String externalReference = trimToNull(request.externalReference());
        if (paymentMethod.needsExternalReference() && externalReference == null) {
            throw new IllegalArgumentException(paymentMethod.name() + "必须填写外部凭证号");
        }
        if (request.salesEmployeeId() != null) validateSalesEmployee(request.storeId(), request.salesEmployeeId());
        LocalDate startDate = request.startDate() == null ? LocalDate.now() : request.startDate();
        if (startDate.isBefore(LocalDate.now().minusDays(30)) || startDate.isAfter(LocalDate.now().plusDays(30))) {
            throw new IllegalArgumentException("次卡生效日期只能在当前日期前后30天内");
        }
        return repository.purchase(new PurchaseMemberCardDraft(
                numbers.cardSaleNo(), memberId, cardType, request.quantity(), request.storeId(),
                storeName(request.storeId()), request.salesEmployeeId(), paymentMethod.id(), paymentMethod.name(),
                externalReference, startDate.atStartOfDay(), request.idempotencyKey(), currentUserId(username)));
    }

    private List<CardServiceRule> validateRules(List<CardServiceRuleRequest> requests, List<Long> storeIds) {
        Set<Long> seen = new HashSet<>();
        List<CardServiceRule> result = new ArrayList<>();
        for (CardServiceRuleRequest request : requests) {
            if (!seen.add(request.serviceId())) throw new IllegalArgumentException("次卡项目不能重复");
            ServiceItemSummary service = null;
            for (long storeId : storeIds) {
                ServiceItemSummary storeService = masterData.services(storeId, null).stream()
                        .filter(item -> item.id() == request.serviceId() && "ACTIVE".equals(item.status())
                                && "ON_SALE".equals(item.saleStatus()))
                        .findFirst().orElseThrow(() -> new IllegalArgumentException("所选项目未在全部适用门店上架"));
                if (service == null) service = storeService;
            }
            result.add(new CardServiceRule(
                    service.id(), service.code(), service.name(), times(request.includedTimes()),
                    times(request.deductTimes()), request.priority()));
        }
        return result;
    }

    private <T> List<T> distinct(List<T> values, String message) {
        List<T> result = values.stream().distinct().toList();
        if (result.size() != values.size()) throw new IllegalArgumentException(message);
        return result;
    }

    private MemberDetail requireMember(long id, boolean active) {
        MemberDetail member = members.findById(id).orElseThrow(() -> new ResourceNotFoundException("会员不存在"));
        if (active && !"ACTIVE".equals(member.status())) throw new IllegalArgumentException("会员当前状态不可办理次卡");
        return member;
    }

    private void validateSalesEmployee(long storeId, long employeeId) {
        EmployeeSummary employee = masterData.employees(storeId, null).stream()
                .filter(item -> item.id() == employeeId && item.canSell() && "ACTIVE".equals(item.status()))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("销售员工不存在或当前不可售卡"));
        if (employee.storeId() != storeId) throw new IllegalArgumentException("销售员工不属于当前门店");
    }

    private void validateStore(long storeId) {
        if (accessCatalog.stores().stream().noneMatch(item -> item.id() == storeId && "ACTIVE".equals(item.status()))) {
            throw new IllegalArgumentException("所选门店不存在或已停用");
        }
    }

    private String storeName(long storeId) {
        return accessCatalog.stores().stream().filter(item -> item.id() == storeId)
                .map(item -> item.name()).findFirst().orElseThrow();
    }

    private String normalizeStatus(String value, Set<String> allowed, String message) {
        String normalized = trimToNull(value);
        if (normalized == null) return null;
        normalized = normalized.toUpperCase(Locale.ROOT);
        if (!allowed.contains(normalized)) throw new IllegalArgumentException(message);
        return normalized;
    }

    private long currentUserId(String username) { return accessCatalog.userIdentity(username).id(); }
    private BigDecimal money(BigDecimal value) { return value.setScale(4, RoundingMode.HALF_UP); }
    private BigDecimal times(BigDecimal value) { return value.setScale(4, RoundingMode.HALF_UP); }
    private String trimToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
}
