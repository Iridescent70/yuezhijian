package com.yuezhijian.server.visit;

import java.util.List;
import java.util.Optional;

public interface VisitRepository {
    List<VisitTaskSummary> tasks(VisitTaskQuery query);

    Optional<VisitTaskDetail> findById(long id);

    Optional<VisitTaskDetail> findByBillId(long billId);

    VisitTaskDetail create(VisitTaskDraft draft);

    VisitTaskDetail claimParticipant(long taskId, long participantId, long employeeId, String employeeName);

    VisitTaskDetail appendRecord(VisitRecordDraft draft);

    VisitTaskDetail complete(long taskId, String conclusion, long operatorId);

    void cancelPendingByBill(long billId, String reason, long operatorId);
}
