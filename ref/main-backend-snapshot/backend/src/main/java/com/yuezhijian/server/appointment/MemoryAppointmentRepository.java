package com.yuezhijian.server.appointment;

import com.yuezhijian.server.common.DuplicateResourceException;
import com.yuezhijian.server.iam.AccessCatalogService;
import com.yuezhijian.server.masterdata.EmployeeSummary;
import com.yuezhijian.server.masterdata.MasterDataRepository;
import com.yuezhijian.server.masterdata.WorkstationSummary;
import com.yuezhijian.server.member.MemberRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("memory")
public class MemoryAppointmentRepository implements AppointmentRepository {
    private static final List<String> OCCUPYING_STATUSES = List.of(
            "PENDING_CONFIRM", "CONFIRMED", "ARRIVED", "SERVING");

    private final Map<Long, AppointmentDetail> appointments = new LinkedHashMap<>();
    private final Map<String, Long> idempotencyKeys = new LinkedHashMap<>();
    private final AtomicLong ids = new AtomicLong(1000);
    private final AtomicLong historyIds = new AtomicLong(5000);
    private final MemberRepository members;
    private final MasterDataRepository masterData;
    private final AccessCatalogService accessCatalog;

    public MemoryAppointmentRepository(
            MemberRepository members,
            MasterDataRepository masterData,
            AccessCatalogService accessCatalog) {
        this.members = members;
        this.masterData = masterData;
        this.accessCatalog = accessCatalog;
    }

    @Override
    public synchronized List<AppointmentSummary> search(AppointmentQuery query) {
        LocalDateTime from = query.startDate().atStartOfDay();
        LocalDateTime until = query.endDate().plusDays(1).atStartOfDay();
        return appointments.values().stream().map(AppointmentDetail::appointment)
                .filter(item -> item.storeId() == query.storeId())
                .filter(item -> !item.startAt().isBefore(from) && item.startAt().isBefore(until))
                .filter(item -> query.status() == null || query.status().equals(item.status()))
                .sorted(Comparator.comparing(AppointmentSummary::startAt).thenComparingLong(AppointmentSummary::id))
                .toList();
    }

    @Override
    public synchronized Optional<AppointmentDetail> findById(long id) {
        return Optional.ofNullable(appointments.get(id));
    }

    @Override
    public synchronized Optional<CreatedAppointment> findByIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null) return Optional.empty();
        Long id = idempotencyKeys.get(idempotencyKey);
        if (id == null) return Optional.empty();
        AppointmentSummary item = appointments.get(id).appointment();
        return Optional.of(new CreatedAppointment(item.id(), item.appointmentNo(), item.status(), item.version()));
    }

    @Override
    public synchronized boolean hasConflict(
            long storeId, long employeeId, Long workstationId,
            LocalDateTime startAt, LocalDateTime endAt, Long excludeAppointmentId) {
        return appointments.values().stream().map(AppointmentDetail::appointment)
                .filter(item -> item.storeId() == storeId)
                .filter(item -> excludeAppointmentId == null || item.id() != excludeAppointmentId)
                .filter(item -> OCCUPYING_STATUSES.contains(item.status()))
                .filter(item -> startAt.isBefore(item.endAt()) && endAt.isAfter(item.startAt()))
                .anyMatch(item -> Objects.equals(item.employeeId(), employeeId)
                        || workstationId != null && Objects.equals(item.workstationId(), workstationId));
    }

    @Override
    public synchronized CreatedAppointment create(AppointmentDraft draft) {
        Optional<CreatedAppointment> existing = findByIdempotencyKey(draft.idempotencyKey());
        if (existing.isPresent()) return existing.get();
        if (hasConflict(draft.storeId(), draft.employeeId(), draft.workstationId(),
                draft.startAt(), draft.endAt(), null)) {
            throw new DuplicateResourceException("所选技师或工位在该时段已被预约");
        }
        long id = ids.incrementAndGet();
        String version = "1";
        AppointmentSummary summary = new AppointmentSummary(
                id, draft.appointmentNo(), draft.memberId(), customerName(draft), customerMobile(draft),
                draft.storeId(), storeName(draft.storeId()), draft.sourceType(), draft.appointmentType(),
                draft.startAt(), draft.endAt(), draft.personCount(), draft.employeeId(),
                employeeName(draft.storeId(), draft.employeeId()), draft.workstationId(),
                workstationName(draft.storeId(), draft.workstationId()), serviceNames(draft.services()),
                draft.note(), AppointmentStatus.PENDING_CONFIRM.name(), version);
        AppointmentHistoryItem history = new AppointmentHistoryItem(
                historyIds.incrementAndGet(), null, AppointmentStatus.PENDING_CONFIRM.name(), null,
                "创建预约", LocalDateTime.now(), draft.operatorId());
        appointments.put(id, new AppointmentDetail(summary, draft.services(), List.of(history)));
        if (draft.idempotencyKey() != null) idempotencyKeys.put(draft.idempotencyKey(), id);
        return new CreatedAppointment(id, draft.appointmentNo(), summary.status(), version);
    }

    @Override
    public synchronized AppointmentDetail update(AppointmentUpdate update) {
        AppointmentDetail current = requireVersion(update.id(), update.version());
        AppointmentSummary old = current.appointment();
        String version = nextVersion(old.version());
        AppointmentSummary changed = new AppointmentSummary(
                old.id(), old.appointmentNo(), old.memberId(), old.customerName(), old.maskedMobile(),
                old.storeId(), old.storeName(), old.sourceType(), old.appointmentType(), update.startAt(),
                update.endAt(), update.personCount(), update.employeeId(),
                employeeName(old.storeId(), update.employeeId()), update.workstationId(),
                workstationName(old.storeId(), update.workstationId()), serviceNames(update.services()),
                update.note(), old.status(), version);
        List<AppointmentHistoryItem> history = new ArrayList<>(current.history());
        history.add(new AppointmentHistoryItem(
                historyIds.incrementAndGet(), old.status(), old.status(), "RESCHEDULED",
                "修改预约时间或项目", LocalDateTime.now(), update.operatorId()));
        AppointmentDetail result = new AppointmentDetail(changed, update.services(), history);
        appointments.put(update.id(), result);
        return result;
    }

    @Override
    public synchronized AppointmentDetail transition(AppointmentStatusChange change) {
        AppointmentDetail current = requireVersion(change.id(), change.version());
        AppointmentSummary old = current.appointment();
        String version = nextVersion(old.version());
        AppointmentSummary changed = new AppointmentSummary(
                old.id(), old.appointmentNo(), old.memberId(), old.customerName(), old.maskedMobile(),
                old.storeId(), old.storeName(), old.sourceType(), old.appointmentType(), old.startAt(), old.endAt(),
                change.personCount() == null ? old.personCount() : change.personCount(), old.employeeId(),
                old.employeeName(), old.workstationId(), old.workstationName(), old.serviceNames(), old.note(),
                change.toStatus(), version);
        List<AppointmentHistoryItem> history = new ArrayList<>(current.history());
        history.add(new AppointmentHistoryItem(
                historyIds.incrementAndGet(), change.fromStatus(), change.toStatus(), change.reasonCode(),
                change.note(), LocalDateTime.now(), change.operatorId()));
        AppointmentDetail result = new AppointmentDetail(changed, current.services(), history);
        appointments.put(change.id(), result);
        return result;
    }

    @Override
    public void linkBill(long appointmentId, long billId, long operatorId) {
        if (!appointments.containsKey(appointmentId)) throw new IllegalArgumentException("预约不存在");
    }

    private AppointmentDetail requireVersion(long id, String version) {
        AppointmentDetail current = appointments.get(id);
        if (current == null) throw new IllegalArgumentException("预约不存在");
        if (!current.appointment().version().equals(version)) {
            throw new DuplicateResourceException("预约已被他人修改，请刷新后重试");
        }
        return current;
    }

    private String customerName(AppointmentDraft draft) {
        return draft.memberId() == null ? draft.guestName()
                : members.findById(draft.memberId()).orElseThrow().fullName();
    }

    private String customerMobile(AppointmentDraft draft) {
        if (draft.memberId() != null) return members.findById(draft.memberId()).orElseThrow().maskedMobile();
        return draft.guestMobile() == null ? null
                : "*******" + draft.guestMobile().substring(draft.guestMobile().length() - 4);
    }

    private String employeeName(long storeId, long employeeId) {
        return masterData.employees(storeId, null).stream()
                .filter(item -> item.id() == employeeId).map(EmployeeSummary::name).findFirst().orElse("未知技师");
    }

    private String workstationName(long storeId, long workstationId) {
        return masterData.workstations(storeId).stream()
                .filter(item -> item.id() == workstationId).map(WorkstationSummary::name).findFirst().orElse("未知工位");
    }

    private String storeName(long storeId) {
        return accessCatalog.stores().stream().filter(item -> item.id() == storeId)
                .map(item -> item.name()).findFirst().orElse("未知门店");
    }

    private String serviceNames(List<AppointmentServiceLine> services) {
        return services.stream().map(AppointmentServiceLine::serviceName).collect(Collectors.joining("、"));
    }

    private String nextVersion(String version) {
        return Long.toString(Long.parseLong(version) + 1);
    }
}
