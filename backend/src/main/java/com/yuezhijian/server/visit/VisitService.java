package com.yuezhijian.server.visit;

import com.yuezhijian.server.common.ResourceNotFoundException;
import com.yuezhijian.server.feedback.ServiceFeedbackService;
import com.yuezhijian.server.iam.AccessCatalogService;
import com.yuezhijian.server.masterdata.EmployeeSummary;
import com.yuezhijian.server.masterdata.MasterDataRepository;
import com.yuezhijian.server.trade.BillDetail;
import com.yuezhijian.server.trade.BillLine;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VisitService {
    private static final Set<String> STATUSES = Set.of("PENDING", "OVERDUE", "COMPLETED", "CANCELLED");
    private static final Set<String> RESULT_CODES = Set.of("CONTACTED", "NO_ANSWER", "DECLINED", "FOLLOW_UP");

    private final VisitRepository repository;
    private final MasterDataRepository masterData;
    private final AccessCatalogService accessCatalog;
    private final VisitNumberGenerator numbers;
    private final ServiceFeedbackService feedback;

    public VisitService(
            VisitRepository repository,
            MasterDataRepository masterData,
            AccessCatalogService accessCatalog,
            VisitNumberGenerator numbers,
            ServiceFeedbackService feedback) {
        this.repository = repository;
        this.masterData = masterData;
        this.accessCatalog = accessCatalog;
        this.numbers = numbers;
        this.feedback = feedback;
    }

    public List<VisitTaskSummary> tasks(
            Long storeId, Long employeeId, String status, LocalDate dueDate, String keyword) {
        if (storeId != null && accessCatalog.stores().stream().noneMatch(store -> store.id() == storeId)) {
            throw new IllegalArgumentException("无权查看所选门店的回访任务");
        }
        String normalizedStatus = trimToNull(status) == null ? null : status.trim().toUpperCase(Locale.ROOT);
        if (normalizedStatus != null && !STATUSES.contains(normalizedStatus)) {
            throw new IllegalArgumentException("回访任务状态无效");
        }
        return repository.tasks(new VisitTaskQuery(
                storeId, employeeId, normalizedStatus, dueDate, trimToNull(keyword)));
    }

    public VisitTaskDetail detail(long id) {
        return repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("回访任务不存在"));
    }

    @Transactional
    public VisitTaskDetail ensureForSettledBill(BillDetail bill, long operatorId) {
        if (bill.bill().memberId() == null || !"SETTLED".equals(bill.bill().status())) return null;
        var existing = repository.findByBillId(bill.bill().id());
        if (existing.isPresent()) return existing.get();

        Map<Long, List<String>> servicesByEmployee = new LinkedHashMap<>();
        Map<Long, String> employeeNames = new LinkedHashMap<>();
        for (BillLine line : bill.lines()) {
            if (!"SERVICE".equals(line.itemType()) || line.employeeId() == null) continue;
            servicesByEmployee.computeIfAbsent(line.employeeId(), ignored -> new ArrayList<>()).add(line.itemName());
            employeeNames.putIfAbsent(line.employeeId(), line.employeeName());
        }
        List<VisitParticipantDraft> participants = new ArrayList<>();
        servicesByEmployee.forEach((employeeId, services) -> participants.add(new VisitParticipantDraft(
                employeeId, employeeNames.get(employeeId), String.join("、", services))));
        if (participants.isEmpty()) {
            participants.add(new VisitParticipantDraft(null, "待分配", bill.lines().stream()
                    .filter(line -> "SERVICE".equals(line.itemType()))
                    .map(BillLine::itemName).distinct().reduce((left, right) -> left + "、" + right).orElse("消费回访")));
        }
        LocalDateTime settledAt = bill.bill().settledAt() == null ? LocalDateTime.now() : bill.bill().settledAt();
        return repository.create(new VisitTaskDraft(
                numbers.taskNo(), bill.bill().memberId(), bill.bill().id(), bill.bill().billNo(),
                bill.bill().customerName(), bill.bill().maskedMobile(), bill.bill().storeId(), bill.bill().storeName(),
                settledAt.plusHours(24), settledAt, participants, operatorId));
    }

    @Transactional
    public VisitTaskDetail addRecord(long taskId, CreateVisitRecordRequest request, String username) {
        VisitTaskDetail detail = detail(taskId);
        if (Set.of("COMPLETED", "CANCELLED").contains(detail.task().status())) {
            throw new IllegalArgumentException("已完成或已取消的回访任务不能继续登记");
        }
        String resultCode = request.resultCode().trim().toUpperCase(Locale.ROOT);
        if (!RESULT_CODES.contains(resultCode)) throw new IllegalArgumentException("回访结果无效");
        boolean terminal = Set.of("CONTACTED", "DECLINED").contains(resultCode);
        if ("CONTACTED".equals(resultCode) && request.satisfactionScore() == null) {
            throw new IllegalArgumentException("已联系会员时必须填写满意度");
        }
        if (!"CONTACTED".equals(resultCode) && request.satisfactionScore() != null) {
            throw new IllegalArgumentException("只有已联系会员时可以填写满意度");
        }
        if (!terminal && (request.nextFollowAt() == null || !request.nextFollowAt().isAfter(LocalDateTime.now()))) {
            throw new IllegalArgumentException("未完成回访时必须填写未来的下次跟进时间");
        }
        String content = trimToNull(request.content());
        if (request.complaintFlag() && content == null) throw new IllegalArgumentException("标记客诉时必须填写情况说明");

        EmployeeSummary employee = masterData.employees(detail.task().storeId(), null).stream()
                .filter(item -> item.id() == request.employeeId() && "ACTIVE".equals(item.status()))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("回访员工不属于当前门店或已停用"));
        VisitParticipantItem participant = detail.participants().stream()
                .filter(item -> request.employeeId().equals(item.employeeId())).findFirst().orElse(null);
        if (participant == null) {
            VisitParticipantItem unassigned = detail.participants().stream()
                    .filter(item -> item.employeeId() == null).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("该员工不在此账单的回访参与人中"));
            detail = repository.claimParticipant(taskId, unassigned.id(), employee.id(), employee.name());
            participant = detail.participants().stream()
                    .filter(item -> request.employeeId().equals(item.employeeId())).findFirst().orElseThrow();
        }
        if ("COMPLETED".equals(participant.status())) throw new IllegalArgumentException("该技师的回访已经完成");
        long operatorId = accessCatalog.userIdentity(username).id();
        VisitTaskDetail updated = repository.appendRecord(new VisitRecordDraft(
                taskId, participant.id(), employee.id(), employee.name(), resultCode, request.satisfactionScore(),
                request.complaintFlag(), content, request.nextFollowAt(), terminal, operatorId));
        if (request.complaintFlag()) feedback.ensureFromVisit(updated, updated.records().getLast(), operatorId);
        return updated;
    }

    @Transactional
    public VisitTaskDetail complete(long taskId, CompleteVisitTaskRequest request, String username) {
        VisitTaskDetail detail = detail(taskId);
        if ("CANCELLED".equals(detail.task().status())) throw new IllegalArgumentException("已取消的任务不能完成");
        if (detail.participants().stream().anyMatch(item -> !"COMPLETED".equals(item.status()))) {
            throw new IllegalArgumentException("仍有技师未完成回访，不能结束任务");
        }
        return repository.complete(taskId, request.conclusion().trim(), accessCatalog.userIdentity(username).id());
    }

    @Transactional
    public void cancelPendingByBill(long billId, String reason, long operatorId) {
        repository.cancelPendingByBill(billId, reason, operatorId);
    }

    private static String trimToNull(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim();
    }
}
