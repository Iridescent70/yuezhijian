package com.yuezhijian.server.commission;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface CommissionRepository {
    List<CommissionPlan> plans(String keyword, String status);
    Optional<CommissionPlan> findPlan(long id);
    boolean existsPlanCode(String code);
    CommissionPlan createPlan(CommissionPlan plan, long operatorId);
    CommissionPlan updatePlan(CommissionPlan plan, long operatorId);
    void snapshotPlan(long planId, long operatorId);
    Optional<CommissionPlan> applicablePlan(String scene, long storeId, Long positionId, LocalDate businessDate);
    List<CommissionLedgerItem> ledgers(CommissionLedgerQuery query);
    Optional<CommissionLedgerItem> findLedgerByCorrelation(String correlationId);
    CommissionLedgerItem appendLedger(CommissionLedgerDraft draft);
    List<CommissionLedgerItem> originalBillLedgers(long billId);
}
