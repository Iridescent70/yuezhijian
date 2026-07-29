package com.yuezhijian.server.commission;

import com.yuezhijian.server.common.DuplicateResourceException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("memory")
public class MemoryCommissionRepository implements CommissionRepository {
    private final AtomicLong planIds = new AtomicLong();
    private final AtomicLong ledgerIds = new AtomicLong();
    private final List<CommissionPlan> plans = new ArrayList<>();
    private final List<CommissionLedgerItem> ledgers = new ArrayList<>();

    @Override
    public synchronized List<CommissionPlan> plans(String keyword, String status) {
        String normalized = keyword == null ? null : keyword.toLowerCase(Locale.ROOT);
        return plans.stream()
                .filter(plan -> normalized == null || plan.code().toLowerCase(Locale.ROOT).contains(normalized)
                        || plan.name().toLowerCase(Locale.ROOT).contains(normalized))
                .filter(plan -> status == null || status.equals(plan.status()))
                .sorted(Comparator.comparingLong(CommissionPlan::id).reversed())
                .toList();
    }

    @Override
    public synchronized Optional<CommissionPlan> findPlan(long id) {
        return plans.stream().filter(plan -> plan.id() == id).findFirst();
    }

    @Override
    public synchronized boolean existsPlanCode(String code) {
        return plans.stream().anyMatch(plan -> plan.code().equals(code));
    }

    @Override
    public synchronized CommissionPlan createPlan(CommissionPlan plan, long operatorId) {
        if (existsPlanCode(plan.code())) throw new DuplicateResourceException("提成方案编码已存在");
        CommissionPlan created = copy(plan, planIds.incrementAndGet(), 1, "1");
        plans.add(created);
        return created;
    }

    @Override
    public synchronized CommissionPlan updatePlan(CommissionPlan plan, long operatorId) {
        CommissionPlan current = findPlan(plan.id()).orElseThrow();
        if (!current.version().equals(plan.version())) {
            throw new DuplicateResourceException("提成方案已被他人修改，请刷新后重试");
        }
        CommissionPlan updated = copy(plan, plan.id(), current.ruleVersion() + 1,
                String.valueOf(Long.parseLong(current.version()) + 1));
        plans.removeIf(item -> item.id() == plan.id());
        plans.add(updated);
        return updated;
    }

    @Override
    public void snapshotPlan(long planId, long operatorId) {
        // 内存开发档只保留当前方案；正式SQL档由comm_plan_revision保存完整版本。
    }

    @Override
    public synchronized Optional<CommissionPlan> applicablePlan(
            String scene, long storeId, Long positionId, LocalDate businessDate) {
        return plans.stream()
                .filter(plan -> "ACTIVE".equals(plan.status()) && scene.equals(plan.scene()))
                .filter(plan -> plan.storeId() == null || plan.storeId() == storeId)
                .filter(plan -> plan.positionId() == null || java.util.Objects.equals(plan.positionId(), positionId))
                .filter(plan -> !plan.effectiveFrom().isAfter(businessDate))
                .filter(plan -> plan.effectiveTo() == null || !plan.effectiveTo().isBefore(businessDate))
                .sorted(Comparator
                        .comparingInt((CommissionPlan plan) -> specificity(plan)).reversed()
                        .thenComparing(CommissionPlan::ruleVersion, Comparator.reverseOrder())
                        .thenComparing(CommissionPlan::id, Comparator.reverseOrder()))
                .findFirst();
    }

    @Override
    public synchronized List<CommissionLedgerItem> ledgers(CommissionLedgerQuery query) {
        return ledgers.stream()
                .filter(item -> query.employeeId() == null || item.employeeId() == query.employeeId())
                .filter(item -> query.storeId() == null || item.storeId() == query.storeId())
                .filter(item -> query.startDate() == null || !item.occurredAt().toLocalDate().isBefore(query.startDate()))
                .filter(item -> query.endDate() == null || !item.occurredAt().toLocalDate().isAfter(query.endDate()))
                .filter(item -> query.direction() == null || ("POSITIVE".equals(query.direction())
                        ? item.commissionAmount().signum() >= 0 : item.commissionAmount().signum() < 0))
                .filter(item -> query.calculationStatus() == null
                        || query.calculationStatus().equals(item.calculationStatus()))
                .sorted(Comparator.comparing(CommissionLedgerItem::occurredAt).reversed()
                        .thenComparing(CommissionLedgerItem::id, Comparator.reverseOrder()))
                .toList();
    }

    @Override
    public synchronized Optional<CommissionLedgerItem> findLedgerByCorrelation(String correlationId) {
        return ledgers.stream().filter(item -> item.correlationId().equals(correlationId)).findFirst();
    }

    @Override
    public synchronized CommissionLedgerItem appendLedger(CommissionLedgerDraft draft) {
        Optional<CommissionLedgerItem> existing = findLedgerByCorrelation(draft.correlationId());
        if (existing.isPresent()) return existing.get();
        long id = ledgerIds.incrementAndGet();
        CommissionLedgerItem item = new CommissionLedgerItem(
                id, draft.ledgerNo(), draft.employeeId(), employeeName(draft.employeeId()), draft.storeId(),
                storeName(draft.storeId()), draft.commissionType(), draft.sourceType(), draft.sourceId(),
                draft.sourceNo(), draft.sourceLineId(), draft.sourceLineName(), draft.baseAmount(), draft.rate(),
                draft.commissionAmount(), draft.calculationStatus(), draft.planId(), draft.planName(),
                draft.planRuleVersion(), draft.formulaSnapshot(), draft.occurredAt(), draft.correlationId(),
                draft.reversedLedgerId());
        ledgers.add(item);
        return item;
    }

    @Override
    public synchronized List<CommissionLedgerItem> originalBillLedgers(long billId) {
        return ledgers.stream()
                .filter(item -> "BILL".equals(item.sourceType()) && item.sourceId() == billId)
                .filter(item -> item.reversedLedgerId() == null && item.commissionAmount().signum() >= 0)
                .toList();
    }

    private CommissionPlan copy(CommissionPlan plan, long id, int ruleVersion, String version) {
        return new CommissionPlan(id, plan.code(), plan.name(), plan.scene(), plan.calculationMode(), plan.rate(),
                plan.fixedAmount(), plan.storeId(), storeNameOrNull(plan.storeId()), plan.positionId(),
                positionName(plan.positionId()), plan.effectiveFrom(), plan.effectiveTo(), plan.status(), ruleVersion,
                version);
    }

    private static int specificity(CommissionPlan plan) {
        return (plan.storeId() == null ? 0 : 1) + (plan.positionId() == null ? 0 : 2);
    }

    private String employeeName(long id) { return id == 101L ? "安然" : id == 102L ? "若溪" : "员工" + id; }
    private String storeName(long id) { return id == 1L ? "悦指间总部" : "悦指间示范店"; }
    private String storeNameOrNull(Long id) { return id == null ? null : storeName(id); }
    private String positionName(Long id) { return id == null ? null : id == 1L ? "美甲技师" : "职务" + id; }
}
