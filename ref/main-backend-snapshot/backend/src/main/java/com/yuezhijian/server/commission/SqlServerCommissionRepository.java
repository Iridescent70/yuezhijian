package com.yuezhijian.server.commission;

import com.yuezhijian.server.common.DuplicateResourceException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("sqlserver")
public class SqlServerCommissionRepository implements CommissionRepository {
    private final CommissionMapper mapper;

    public SqlServerCommissionRepository(CommissionMapper mapper) { this.mapper = mapper; }

    @Override
    public List<CommissionPlan> plans(String keyword, String status) { return mapper.findPlans(keyword, status); }

    @Override
    public Optional<CommissionPlan> findPlan(long id) { return Optional.ofNullable(mapper.findPlan(id)); }

    @Override
    public boolean existsPlanCode(String code) { return mapper.countPlanCode(code) > 0; }

    @Override
    public CommissionPlan createPlan(CommissionPlan plan, long operatorId) {
        mapper.insertPlan(plan, operatorId);
        return mapper.findPlans(plan.code(), null).stream()
                .filter(item -> item.code().equals(plan.code())).findFirst().orElseThrow();
    }

    @Override
    public CommissionPlan updatePlan(CommissionPlan plan, long operatorId) {
        if (mapper.updatePlan(plan, operatorId) != 1) {
            throw new DuplicateResourceException("提成方案已被他人修改，请刷新后重试");
        }
        return Optional.ofNullable(mapper.findPlan(plan.id())).orElseThrow();
    }

    @Override
    public void snapshotPlan(long planId, long operatorId) {
        if (mapper.insertPlanRevision(planId, operatorId) != 1) {
            throw new IllegalStateException("提成方案版本快照写入失败");
        }
    }

    @Override
    public Optional<CommissionPlan> applicablePlan(
            String scene, long storeId, Long positionId, LocalDate businessDate) {
        return Optional.ofNullable(mapper.findApplicablePlan(scene, storeId, positionId, businessDate));
    }

    @Override
    public List<CommissionLedgerItem> ledgers(CommissionLedgerQuery query) { return mapper.findLedgers(query); }

    @Override
    public Optional<CommissionLedgerItem> findLedgerByCorrelation(String correlationId) {
        return Optional.ofNullable(mapper.findLedgerByCorrelation(correlationId));
    }

    @Override
    public CommissionLedgerItem appendLedger(CommissionLedgerDraft draft) {
        Optional<CommissionLedgerItem> existing = findLedgerByCorrelation(draft.correlationId());
        if (existing.isPresent()) return existing.get();
        mapper.insertLedger(draft);
        return Optional.ofNullable(mapper.findLedgerByCorrelation(draft.correlationId())).orElseThrow();
    }

    @Override
    public List<CommissionLedgerItem> originalBillLedgers(long billId) {
        return mapper.findOriginalBillLedgers(billId);
    }

    @Override
    public List<CommissionLedgerItem> originalCardSaleLedgers(long memberCardId) {
        return mapper.findOriginalCardSaleLedgers(memberCardId);
    }
}
