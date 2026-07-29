package com.yuezhijian.server.visit;

import com.yuezhijian.server.common.DuplicateResourceException;
import com.yuezhijian.server.common.ResourceNotFoundException;
import java.util.List;
import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("sqlserver")
public class SqlServerVisitRepository implements VisitRepository {
    private final VisitMapper mapper;

    public SqlServerVisitRepository(VisitMapper mapper) { this.mapper = mapper; }

    @Override
    public List<VisitTaskSummary> tasks(VisitTaskQuery query) { return mapper.findTasks(query); }

    @Override
    public Optional<VisitTaskDetail> findById(long id) {
        return Optional.ofNullable(mapper.findTask(id)).map(this::detail);
    }

    @Override
    public Optional<VisitTaskDetail> findByBillId(long billId) {
        return Optional.ofNullable(mapper.findTaskByBill(billId)).map(this::detail);
    }

    @Override
    public VisitTaskDetail create(VisitTaskDraft draft) {
        Optional<VisitTaskDetail> existing = findByBillId(draft.billId());
        if (existing.isPresent()) return existing.get();
        mapper.insertTask(draft);
        VisitTaskSummary created = Optional.ofNullable(mapper.findTaskByBill(draft.billId())).orElseThrow();
        for (VisitParticipantDraft participant : draft.participants()) {
            mapper.insertParticipant(created.id(), participant, draft.createdBy());
        }
        return findById(created.id()).orElseThrow();
    }

    @Override
    public VisitTaskDetail claimParticipant(
            long taskId, long participantId, long employeeId, String employeeName) {
        if (mapper.claimParticipant(taskId, participantId, employeeId, employeeName) != 1) {
            throw new DuplicateResourceException("回访任务已被其他员工认领，请刷新后重试");
        }
        return findById(taskId).orElseThrow();
    }

    @Override
    public VisitTaskDetail appendRecord(VisitRecordDraft draft) {
        VisitTaskDetail current = findById(draft.taskId())
                .orElseThrow(() -> new ResourceNotFoundException("回访任务不存在"));
        VisitParticipantItem participant = current.participants().stream()
                .filter(item -> item.id() == draft.participantId()).findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("回访参与人不存在"));
        if ("COMPLETED".equals(participant.status())) throw new DuplicateResourceException("该技师回访已完成");
        mapper.insertRecord(draft);
        if (draft.completesParticipant() && mapper.completeParticipant(draft.taskId(), draft.participantId()) != 1) {
            throw new DuplicateResourceException("回访参与项已被其他人处理，请刷新后重试");
        }
        mapper.updateTaskFromRecord(draft);
        mapper.autoCompleteTask(draft.taskId(), draft.createdBy());
        return findById(draft.taskId()).orElseThrow();
    }

    @Override
    public VisitTaskDetail complete(long taskId, String conclusion, long operatorId) {
        if (mapper.completeTask(taskId, conclusion, operatorId) != 1) {
            throw new DuplicateResourceException("回访任务仍有未完成参与人或状态已变化");
        }
        return findById(taskId).orElseThrow();
    }

    @Override
    public void cancelPendingByBill(long billId, String reason, long operatorId) {
        mapper.cancelPendingByBill(billId, reason, operatorId);
    }

    private VisitTaskDetail detail(VisitTaskSummary task) {
        return new VisitTaskDetail(task, mapper.findParticipants(task.id()), mapper.findRecords(task.id()));
    }
}
