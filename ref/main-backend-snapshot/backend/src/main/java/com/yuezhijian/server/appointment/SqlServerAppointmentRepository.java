package com.yuezhijian.server.appointment;

import com.yuezhijian.server.common.DuplicateResourceException;
import com.yuezhijian.server.common.SensitiveDataCodec;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Profile("sqlserver")
public class SqlServerAppointmentRepository implements AppointmentRepository {
    private final AppointmentMapper mapper;
    private final SensitiveDataCodec codec;

    public SqlServerAppointmentRepository(AppointmentMapper mapper, SensitiveDataCodec codec) {
        this.mapper = mapper;
        this.codec = codec;
    }

    @Override
    public List<AppointmentSummary> search(AppointmentQuery query) {
        return mapper.search(query, query.startDate().atStartOfDay(), query.endDate().plusDays(1).atStartOfDay());
    }

    @Override
    public Optional<AppointmentDetail> findById(long id) {
        AppointmentSummary summary = mapper.findSummaryById(id);
        return summary == null ? Optional.empty()
                : Optional.of(new AppointmentDetail(summary, mapper.findServices(id), mapper.findHistory(id)));
    }

    @Override
    public Optional<CreatedAppointment> findByIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null) return Optional.empty();
        AppointmentSummary summary = mapper.findSummaryByIdempotencyKey(idempotencyKey);
        return summary == null ? Optional.empty() : Optional.of(new CreatedAppointment(
                summary.id(), summary.appointmentNo(), summary.status(), summary.version()));
    }

    @Override
    public boolean hasConflict(
            long storeId, long employeeId, Long workstationId,
            LocalDateTime startAt, LocalDateTime endAt, Long excludeAppointmentId) {
        return mapper.countConflicts(
                storeId, employeeId, workstationId, startAt, endAt, excludeAppointmentId) > 0;
    }

    @Override
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public CreatedAppointment create(AppointmentDraft draft) {
        Optional<CreatedAppointment> existing = findByIdempotencyKey(draft.idempotencyKey());
        if (existing.isPresent()) return existing.get();
        assertNoConflict(draft.storeId(), draft.employeeId(), draft.workstationId(),
                draft.startAt(), draft.endAt(), null);
        String ciphertext = draft.guestMobile() == null ? null : codec.encrypt(draft.guestMobile());
        String hash = draft.guestMobile() == null ? null : codec.searchableHash(draft.guestMobile());
        String last4 = draft.guestMobile() == null ? null
                : draft.guestMobile().substring(draft.guestMobile().length() - 4);
        long id = mapper.insertAppointment(new ProtectedAppointmentRow(
                draft.appointmentNo(), draft.memberId(), draft.guestName(), ciphertext, hash, last4,
                draft.storeId(), draft.sourceType(), draft.appointmentType(), draft.startAt(), draft.endAt(),
                draft.personCount(), draft.workstationId(), draft.note(), draft.idempotencyKey(), draft.operatorId()));
        draft.services().forEach(line -> mapper.insertService(id, line));
        mapper.insertEmployee(id, draft.employeeId(), draft.startAt(), draft.endAt(), draft.designated());
        mapper.insertHistory(id, null, AppointmentStatus.PENDING_CONFIRM.name(), null, "创建预约", draft.operatorId());
        AppointmentSummary saved = mapper.findSummaryById(id);
        return new CreatedAppointment(id, saved.appointmentNo(), saved.status(), saved.version());
    }

    @Override
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public AppointmentDetail update(AppointmentUpdate update) {
        AppointmentSummary current = mapper.findSummaryById(update.id());
        if (current == null) throw new IllegalArgumentException("预约不存在");
        assertNoConflict(current.storeId(), update.employeeId(), update.workstationId(),
                update.startAt(), update.endAt(), update.id());
        if (mapper.updateAppointment(update) != 1) {
            throw new DuplicateResourceException("预约已被他人修改，请刷新后重试");
        }
        mapper.deleteServices(update.id());
        mapper.deleteEmployees(update.id());
        update.services().forEach(line -> mapper.insertService(update.id(), line));
        mapper.insertEmployee(
                update.id(), update.employeeId(), update.startAt(), update.endAt(), update.designated());
        mapper.insertHistory(
                update.id(), current.status(), current.status(), "RESCHEDULED", "修改预约时间或项目", update.operatorId());
        return requireDetail(update.id());
    }

    @Override
    @Transactional
    public AppointmentDetail transition(AppointmentStatusChange change) {
        if (mapper.transition(change) != 1) {
            throw new DuplicateResourceException("预约状态已变化，请刷新后重试");
        }
        mapper.insertHistory(
                change.id(), change.fromStatus(), change.toStatus(), change.reasonCode(), change.note(),
                change.operatorId());
        return requireDetail(change.id());
    }

    private AppointmentDetail requireDetail(long id) {
        return findById(id).orElseThrow(() -> new IllegalArgumentException("预约不存在"));
    }

    @Override
    public void linkBill(long appointmentId, long billId, long operatorId) {
        if (mapper.linkBill(appointmentId, billId, operatorId) != 1) {
            throw new DuplicateResourceException("预约已关联其他账单，请刷新后重试");
        }
    }

    private void assertNoConflict(
            long storeId, long employeeId, Long workstationId,
            LocalDateTime startAt, LocalDateTime endAt, Long excludeId) {
        if (hasConflict(storeId, employeeId, workstationId, startAt, endAt, excludeId)) {
            throw new DuplicateResourceException("所选技师或工位在该时段已被预约");
        }
    }
}
