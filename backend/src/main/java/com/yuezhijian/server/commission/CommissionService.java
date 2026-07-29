package com.yuezhijian.server.commission;

import com.yuezhijian.server.asset.CardExchangeResult;
import com.yuezhijian.server.asset.CardSaleResult;
import com.yuezhijian.server.asset.MemberCardSummary;
import com.yuezhijian.server.common.DuplicateResourceException;
import com.yuezhijian.server.common.ResourceNotFoundException;
import com.yuezhijian.server.iam.AccessCatalogService;
import com.yuezhijian.server.masterdata.EmployeeSummary;
import com.yuezhijian.server.masterdata.MasterDataRepository;
import com.yuezhijian.server.trade.BillDetail;
import com.yuezhijian.server.trade.BillLine;
import com.yuezhijian.server.trade.ReversalDetail;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CommissionService {
    private static final Set<String> SCENES = Set.of("SERVICE", "CARD_SALE", "CARD_CONSUME");
    private static final Set<String> MODES = Set.of("RATE", "FIXED", "NONE");
    private static final Set<String> STATUSES = Set.of("ACTIVE", "INACTIVE");
    private static final Set<String> CALCULATION_STATUSES = Set.of("CALCULATED", "PENDING_RULE");

    private final CommissionRepository repository;
    private final MasterDataRepository masterData;
    private final AccessCatalogService accessCatalog;
    private final CommissionNumberGenerator numbers;

    public CommissionService(
            CommissionRepository repository,
            MasterDataRepository masterData,
            AccessCatalogService accessCatalog,
            CommissionNumberGenerator numbers) {
        this.repository = repository;
        this.masterData = masterData;
        this.accessCatalog = accessCatalog;
        this.numbers = numbers;
    }

    public List<CommissionPlan> plans(String keyword, String status) {
        return repository.plans(trimToNull(keyword), normalize(status, STATUSES, "提成方案状态无效"));
    }

    public CommissionPlan plan(long id) {
        return repository.findPlan(id).orElseThrow(() -> new ResourceNotFoundException("提成方案不存在"));
    }

    @Transactional
    public CommissionPlan createPlan(CreateCommissionPlanRequest request, String username) {
        String code = request.code().trim().toUpperCase(Locale.ROOT);
        if (repository.existsPlanCode(code)) throw new DuplicateResourceException("提成方案编码已存在");
        CommissionPlan plan = normalizedPlan(
                0, code, request.name(), request.scene(), request.calculationMode(), request.rate(),
                request.fixedAmount(), request.storeId(), request.positionId(), request.effectiveFrom(),
                request.effectiveTo(), "ACTIVE", 0, null);
        long operatorId = currentUserId(username);
        CommissionPlan created = repository.createPlan(plan, operatorId);
        repository.snapshotPlan(created.id(), operatorId);
        return created;
    }

    @Transactional
    public CommissionPlan updatePlan(long id, UpdateCommissionPlanRequest request, String username) {
        CommissionPlan current = plan(id);
        if (!current.version().equals(request.version())) {
            throw new DuplicateResourceException("提成方案已被他人修改，请刷新后重试");
        }
        CommissionPlan plan = normalizedPlan(
                id, current.code(), request.name(), request.scene(), request.calculationMode(), request.rate(),
                request.fixedAmount(), request.storeId(), request.positionId(), request.effectiveFrom(),
                request.effectiveTo(), request.status(), current.ruleVersion(), request.version());
        long operatorId = currentUserId(username);
        CommissionPlan updated = repository.updatePlan(plan, operatorId);
        repository.snapshotPlan(updated.id(), operatorId);
        return updated;
    }

    public List<CommissionLedgerItem> ledgers(
            Long employeeId, Long storeId, LocalDate startDate, LocalDate endDate,
            String direction, String calculationStatus) {
        if (startDate != null && endDate != null
                && (endDate.isBefore(startDate) || ChronoUnit.DAYS.between(startDate, endDate) > 366)) {
            throw new IllegalArgumentException("提成流水查询范围不能超过一年");
        }
        String normalizedDirection = normalize(direction, Set.of("POSITIVE", "NEGATIVE"), "提成方向无效");
        String normalizedStatus = normalize(calculationStatus, CALCULATION_STATUSES, "计算状态无效");
        return repository.ledgers(new CommissionLedgerQuery(
                employeeId, storeId, startDate, endDate, normalizedDirection, normalizedStatus));
    }

    @Transactional
    public List<CommissionLedgerItem> recordSettledBill(BillDetail bill, long operatorId) {
        if (!"SETTLED".equals(bill.bill().status())) throw new IllegalArgumentException("账单尚未结算");
        LocalDateTime occurredAt = bill.bill().settledAt() == null ? LocalDateTime.now() : bill.bill().settledAt();
        Set<Long> cardConsumedLines = bill.assetUsages().stream()
                .filter(item -> "CARD".equals(item.assetType()) && item.billLineId() != null)
                .map(item -> item.billLineId()).collect(Collectors.toSet());
        List<CommissionLedgerItem> result = new ArrayList<>();
        for (BillLine line : bill.lines()) {
            if (!"SERVICE".equals(line.itemType()) || line.employeeId() == null) continue;
            String correlation = "bill:" + bill.bill().id() + ":line:" + line.id() + ":employee:" + line.employeeId();
            Optional<CommissionLedgerItem> existing = repository.findLedgerByCorrelation(correlation);
            if (existing.isPresent()) {
                result.add(existing.get());
                continue;
            }
            EmployeeSummary employee = employee(line.employeeId(), bill.bill().storeId());
            String scene = cardConsumedLines.contains(line.id()) ? "CARD_CONSUME" : "SERVICE";
            Optional<CommissionPlan> selected = repository.applicablePlan(
                    scene, bill.bill().storeId(), employee.positionId(), occurredAt.toLocalDate());
            BigDecimal base = money(line.receivableAmount());
            String calculationStatus = selected.isPresent() ? "CALCULATED" : "PENDING_RULE";
            BigDecimal rate = selected.map(CommissionPlan::rate).orElse(null);
            BigDecimal amount = selected.map(plan -> calculate(plan, base)).orElse(BigDecimal.ZERO.setScale(4));
            String formula = selected.map(plan -> formula(plan, base, amount, "服务项目"))
                    .orElse("未找到生效" + ("CARD_CONSUME".equals(scene) ? "次卡实耗" : "服务")
                            + "方案；门店=" + bill.bill().storeId() + "，职务=" + employee.positionId()
                            + "，业务日期=" + occurredAt.toLocalDate());
            result.add(repository.appendLedger(new CommissionLedgerDraft(
                    numbers.ledgerNo(), employee.id(), bill.bill().storeId(), scene, "BILL",
                    bill.bill().id(), bill.bill().billNo(), line.id(), line.itemName(), base, rate, amount,
                    calculationStatus, selected.map(CommissionPlan::id).orElse(null),
                    selected.map(CommissionPlan::name).orElse(null),
                    selected.map(CommissionPlan::ruleVersion).orElse(null), formula, occurredAt, correlation,
                    null, operatorId)));
        }
        return List.copyOf(result);
    }

    @Transactional
    public List<CommissionLedgerItem> recordCardSale(
            CardSaleResult sale, long storeId, Long employeeId, LocalDateTime occurredAt, long operatorId) {
        if (employeeId == null) return List.of();
        EmployeeSummary employee = employee(employeeId, storeId);
        List<CommissionLedgerItem> result = new ArrayList<>();
        for (MemberCardSummary card : sale.cards()) {
            result.add(recordCardSaleFact(
                    employee, storeId, "CARD_SALE", sale.orderId(), sale.orderNo(), card.id(),
                    card.cardTypeName(), card.purchasePrice(), occurredAt,
                    "card-sale:order:" + sale.orderId() + ":card:" + card.id() + ":employee:" + employee.id(),
                    operatorId));
        }
        return List.copyOf(result);
    }

    @Transactional
    public List<CommissionLedgerItem> recordCardExchange(
            CardExchangeResult exchange, List<Long> oldCardLineage,
            long storeId, Long employeeId, long operatorId) {
        List<CommissionLedgerItem> result = new ArrayList<>(reverseCardSale(
                oldCardLineage, exchange.oldCard().id(), "CARD_EXCHANGE",
                exchange.exchangeId(), exchange.exchangeNo(), operatorId));
        if (employeeId != null && exchange.differenceAmount().signum() > 0) {
            EmployeeSummary employee = employee(employeeId, storeId);
            result.add(recordCardSaleFact(
                    employee, storeId, "CARD_EXCHANGE", exchange.exchangeId(), exchange.exchangeNo(),
                    exchange.newCard().id(), exchange.newCard().cardTypeName(), exchange.differenceAmount(),
                    exchange.executedAt(), "card-exchange:" + exchange.exchangeId() + ":card:"
                            + exchange.newCard().id() + ":employee:" + employee.id(), operatorId));
        }
        return List.copyOf(result);
    }

    @Transactional
    public List<CommissionLedgerItem> reverseCardSale(
            List<Long> memberCardLineage, long affectedMemberCardId,
            String sourceType, long sourceId, String sourceNo, long operatorId) {
        List<CommissionLedgerItem> result = new ArrayList<>();
        List<CommissionLedgerItem> originals = memberCardLineage.stream()
                .map(repository::originalCardSaleLedgers)
                .filter(items -> !items.isEmpty())
                .findFirst().orElse(List.of());
        for (CommissionLedgerItem original : originals) {
            String correlation = sourceType.toLowerCase(Locale.ROOT) + ':' + sourceId
                    + ":commission:" + original.id();
            Optional<CommissionLedgerItem> existing = repository.findLedgerByCorrelation(correlation);
            if (existing.isPresent()) {
                result.add(existing.get());
                continue;
            }
            result.add(repository.appendLedger(new CommissionLedgerDraft(
                    numbers.ledgerNo(), original.employeeId(), original.storeId(), original.commissionType(),
                    sourceType, sourceId, sourceNo, affectedMemberCardId, original.sourceLineName(),
                    original.baseAmount().negate(), original.rate(), original.commissionAmount().negate(),
                    original.calculationStatus(), original.planId(), original.planName(),
                    original.planRuleVersion(), "冲回售卡提成流水 " + original.ledgerNo()
                            + "；原公式：" + original.formulaSnapshot(), LocalDateTime.now(), correlation,
                    original.id(), operatorId)));
        }
        return List.copyOf(result);
    }

    @Transactional
    public List<CommissionLedgerItem> reverseBill(
            BillDetail bill, ReversalDetail reversal, long operatorId) {
        List<CommissionLedgerItem> result = new ArrayList<>();
        for (CommissionLedgerItem original : repository.originalBillLedgers(bill.bill().id())) {
            String correlation = "reversal:" + reversal.reversal().id() + ":commission:" + original.id();
            Optional<CommissionLedgerItem> existing = repository.findLedgerByCorrelation(correlation);
            if (existing.isPresent()) {
                result.add(existing.get());
                continue;
            }
            result.add(repository.appendLedger(new CommissionLedgerDraft(
                    numbers.ledgerNo(), original.employeeId(), original.storeId(), original.commissionType(),
                    "BILL_REVERSAL", reversal.reversal().id(), reversal.reversal().reversalNo(),
                    original.sourceLineId(), original.sourceLineName(), original.baseAmount().negate(),
                    original.rate(), original.commissionAmount().negate(), original.calculationStatus(),
                    original.planId(), original.planName(), original.planRuleVersion(),
                    "冲回提成流水 " + original.ledgerNo() + "；原公式：" + original.formulaSnapshot(),
                    LocalDateTime.now(), correlation, original.id(), operatorId)));
        }
        return List.copyOf(result);
    }

    private CommissionPlan normalizedPlan(
            long id, String code, String name, String sceneValue, String modeValue, BigDecimal rateValue,
            BigDecimal fixedValue, Long storeId, Long positionId, LocalDate effectiveFrom,
            LocalDate effectiveTo, String statusValue, int ruleVersion, String version) {
        String scene = normalizeRequired(sceneValue, SCENES, "提成场景无效");
        String mode = normalizeRequired(modeValue, MODES, "计算方式无效");
        String status = normalizeRequired(statusValue, STATUSES, "提成方案状态无效");
        if (effectiveTo != null && effectiveTo.isBefore(effectiveFrom)) {
            throw new IllegalArgumentException("失效日期不能早于生效日期");
        }
        if (storeId != null && accessCatalog.stores().stream().noneMatch(store -> store.id() == storeId)) {
            throw new IllegalArgumentException("适用门店不存在");
        }
        if (positionId != null && masterData.positions().stream().noneMatch(position -> position.id() == positionId)) {
            throw new IllegalArgumentException("适用职务不存在");
        }
        BigDecimal rate = rateValue == null ? null : rateValue.setScale(6, RoundingMode.HALF_UP);
        BigDecimal fixed = fixedValue == null ? null : money(fixedValue);
        if ("RATE".equals(mode) && (rate == null || rate.signum() < 0 || rate.compareTo(BigDecimal.ONE) > 0)) {
            throw new IllegalArgumentException("比例提成的费率必须在0到1之间");
        }
        if ("FIXED".equals(mode) && (fixed == null || fixed.signum() < 0)) {
            throw new IllegalArgumentException("固定提成金额不能小于0");
        }
        if (!"RATE".equals(mode)) rate = null;
        if (!"FIXED".equals(mode)) fixed = null;
        return new CommissionPlan(
                id, code, name.trim(), scene, mode, rate, fixed, storeId, null, positionId, null,
                effectiveFrom, effectiveTo, status, ruleVersion, version);
    }

    private BigDecimal calculate(CommissionPlan plan, BigDecimal base) {
        return switch (plan.calculationMode()) {
            case "RATE" -> money(base.multiply(plan.rate()));
            case "FIXED" -> money(plan.fixedAmount());
            case "NONE" -> BigDecimal.ZERO.setScale(4);
            default -> throw new IllegalArgumentException("不支持的提成计算方式");
        };
    }

    private CommissionLedgerItem recordCardSaleFact(
            EmployeeSummary employee, long storeId, String sourceType, long sourceId, String sourceNo,
            long memberCardId, String cardTypeName, BigDecimal baseValue, LocalDateTime occurredAt,
            String correlation, long operatorId) {
        Optional<CommissionLedgerItem> existing = repository.findLedgerByCorrelation(correlation);
        if (existing.isPresent()) return existing.get();
        Optional<CommissionPlan> selected = repository.applicablePlan(
                "CARD_SALE", storeId, employee.positionId(), occurredAt.toLocalDate());
        BigDecimal base = money(baseValue);
        String calculationStatus = selected.isPresent() ? "CALCULATED" : "PENDING_RULE";
        BigDecimal rate = selected.map(CommissionPlan::rate).orElse(null);
        BigDecimal amount = selected.map(plan -> calculate(plan, base)).orElse(BigDecimal.ZERO.setScale(4));
        String formula = selected.map(plan -> formula(plan, base, amount, "张卡"))
                .orElse("未找到生效售卡方案；门店=" + storeId + "，职务=" + employee.positionId()
                        + "，业务日期=" + occurredAt.toLocalDate());
        return repository.appendLedger(new CommissionLedgerDraft(
                numbers.ledgerNo(), employee.id(), storeId, "CARD_SALE", sourceType, sourceId, sourceNo,
                memberCardId, cardTypeName, base, rate, amount, calculationStatus,
                selected.map(CommissionPlan::id).orElse(null), selected.map(CommissionPlan::name).orElse(null),
                selected.map(CommissionPlan::ruleVersion).orElse(null), formula, occurredAt, correlation,
                null, operatorId));
    }

    private String formula(CommissionPlan plan, BigDecimal base, BigDecimal amount, String unitName) {
        return switch (plan.calculationMode()) {
            case "RATE" -> "方案=" + plan.name() + " v" + plan.ruleVersion() + "；提成=" + base
                    + "×" + plan.rate() + "=" + amount;
            case "FIXED" -> "方案=" + plan.name() + " v" + plan.ruleVersion() + "；每" + unitName
                    + "固定提成=" + amount;
            case "NONE" -> "方案=" + plan.name() + " v" + plan.ruleVersion() + "；本场景不计提成";
            default -> throw new IllegalArgumentException("不支持的提成计算方式");
        };
    }

    private EmployeeSummary employee(long id, long storeId) {
        return masterData.employees(storeId, null).stream().filter(item -> item.id() == id).findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("账单技师不存在"));
    }

    private String normalizeRequired(String value, Set<String> allowed, String message) {
        String normalized = normalize(value, allowed, message);
        if (normalized == null) throw new IllegalArgumentException(message);
        return normalized;
    }

    private String normalize(String value, Set<String> allowed, String message) {
        String trimmed = trimToNull(value);
        if (trimmed == null) return null;
        String normalized = trimmed.toUpperCase(Locale.ROOT);
        if (!allowed.contains(normalized)) throw new IllegalArgumentException(message);
        return normalized;
    }

    private long currentUserId(String username) { return accessCatalog.userIdentity(username).id(); }
    private BigDecimal money(BigDecimal value) { return value.setScale(4, RoundingMode.HALF_UP); }
    private String trimToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
}
