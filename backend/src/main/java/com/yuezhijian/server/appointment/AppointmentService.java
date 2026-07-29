package com.yuezhijian.server.appointment;

import com.yuezhijian.server.common.DuplicateResourceException;
import com.yuezhijian.server.common.ResourceNotFoundException;
import com.yuezhijian.server.iam.AccessCatalogService;
import com.yuezhijian.server.masterdata.EmployeeSummary;
import com.yuezhijian.server.masterdata.MasterDataRepository;
import com.yuezhijian.server.masterdata.ServiceItemSummary;
import com.yuezhijian.server.masterdata.WorkstationSummary;
import com.yuezhijian.server.member.MemberDetail;
import com.yuezhijian.server.member.MemberRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class AppointmentService {
    private static final Map<AppointmentStatus, Set<AppointmentStatus>> TRANSITIONS = Map.of(
            AppointmentStatus.PENDING_CONFIRM, Set.of(
                    AppointmentStatus.CONFIRMED, AppointmentStatus.CANCELLED, AppointmentStatus.NO_SHOW),
            AppointmentStatus.CONFIRMED, Set.of(
                    AppointmentStatus.ARRIVED, AppointmentStatus.CANCELLED, AppointmentStatus.NO_SHOW),
            AppointmentStatus.ARRIVED, Set.of(AppointmentStatus.SERVING, AppointmentStatus.CANCELLED),
            AppointmentStatus.SERVING, Set.of(AppointmentStatus.COMPLETED));
    private static final List<CancelReasonOption> CANCEL_REASONS = List.of(
            new CancelReasonOption("CUSTOMER_CHANGE", "客户行程有变", false),
            new CancelReasonOption("STORE_CAPACITY", "门店接待能力不足", true),
            new CancelReasonOption("CUSTOMER_NO_SHOW", "客户未按时到店", false),
            new CancelReasonOption("OTHER", "其他", true));

    private final AppointmentRepository repository;
    private final MasterDataRepository masterData;
    private final MemberRepository members;
    private final AccessCatalogService accessCatalog;
    private final AppointmentNumberGenerator numberGenerator;

    public AppointmentService(
            AppointmentRepository repository,
            MasterDataRepository masterData,
            MemberRepository members,
            AccessCatalogService accessCatalog,
            AppointmentNumberGenerator numberGenerator) {
        this.repository = repository;
        this.masterData = masterData;
        this.members = members;
        this.accessCatalog = accessCatalog;
        this.numberGenerator = numberGenerator;
    }

    public List<AppointmentSummary> search(Long storeId, LocalDate startDate, LocalDate endDate, String status) {
        long resolvedStoreId = storeId == null ? accessCatalog.stores().getFirst().id() : storeId;
        validateStore(resolvedStoreId);
        LocalDate resolvedStart = startDate == null ? LocalDate.now() : startDate;
        LocalDate resolvedEnd = endDate == null ? resolvedStart : endDate;
        if (resolvedEnd.isBefore(resolvedStart) || ChronoUnit.DAYS.between(resolvedStart, resolvedEnd) > 31) {
            throw new IllegalArgumentException("预约查询日期范围必须在1至32天内");
        }
        String normalizedStatus = normalizeStatus(status);
        return repository.search(new AppointmentQuery(resolvedStoreId, resolvedStart, resolvedEnd, normalizedStatus));
    }

    public AppointmentDetail detail(long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("预约不存在"));
    }

    public List<AvailabilitySlot> availability(long storeId, long serviceId, long employeeId, LocalDate date) {
        validateStore(storeId);
        ServiceItemSummary service = masterData.services(storeId, null).stream()
                .filter(item -> item.id() == serviceId && "ACTIVE".equals(item.status())
                        && "ON_SALE".equals(item.saleStatus()))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("服务项目未在当前门店上架"));
        EmployeeSummary employee = masterData.employees(storeId, null).stream()
                .filter(item -> item.id() == employeeId && item.canService() && "ACTIVE".equals(item.status()))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("所选技师当前不可预约"));
        List<AvailabilitySlot> slots = new ArrayList<>();
        LocalDateTime cursor = date.atTime(9, 0);
        LocalDateTime closing = date.atTime(22, 0);
        while (!cursor.plusMinutes(service.durationMinutes()).isAfter(closing)) {
            LocalDateTime endAt = cursor.plusMinutes(service.durationMinutes());
            boolean available = !repository.hasConflict(storeId, employee.id(), null, cursor, endAt, null);
            slots.add(new AvailabilitySlot(cursor, endAt, available, available ? null : "技师时段已占用"));
            cursor = cursor.plusMinutes(30);
        }
        return slots;
    }

    public CreatedAppointment create(CreateAppointmentRequest request, String username) {
        String idempotencyKey = trimToNull(request.idempotencyKey());
        Optional<CreatedAppointment> existing = repository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) return existing.get();
        validateStore(request.storeId());
        validateCustomer(request.memberId(), request.guestName(), request.guestMobile());
        List<AppointmentServiceLine> lines = resolveServices(request.storeId(), request.serviceIds());
        LocalDateTime endAt = calculateEnd(request.startAt(), lines);
        validateResource(request.storeId(), request.employeeId(), request.workstationId());
        ensureNoConflict(request.storeId(), request.employeeId(), request.workstationId(),
                request.startAt(), endAt, null);
        return repository.create(new AppointmentDraft(
                numberGenerator.next(), request.memberId(), trimToNull(request.guestName()),
                normalizeMobile(request.guestMobile()), request.storeId(),
                normalizeChoice(request.sourceType(), "PC", Set.of("PC", "MOBILE", "CUSTOMER", "IMPORT"), "预约来源"),
                normalizeChoice(request.appointmentType(), "IN_STORE", Set.of("IN_STORE", "HOME_SERVICE"), "预约类型"),
                request.startAt(), endAt, request.personCount(), request.employeeId(), request.workstationId(),
                lines, request.designated(), trimToNull(request.note()), idempotencyKey,
                currentUserId(username)));
    }

    public AppointmentDetail update(long id, UpdateAppointmentRequest request, String username) {
        AppointmentDetail current = detail(id);
        AppointmentStatus status = AppointmentStatus.valueOf(current.appointment().status());
        if (status != AppointmentStatus.PENDING_CONFIRM && status != AppointmentStatus.CONFIRMED) {
            throw new IllegalArgumentException("当前预约状态不允许改期或修改项目");
        }
        long storeId = current.appointment().storeId();
        List<AppointmentServiceLine> lines = resolveServices(storeId, request.serviceIds());
        LocalDateTime endAt = calculateEnd(request.startAt(), lines);
        validateResource(storeId, request.employeeId(), request.workstationId());
        ensureNoConflict(storeId, request.employeeId(), request.workstationId(),
                request.startAt(), endAt, id);
        return repository.update(new AppointmentUpdate(
                id, request.startAt(), endAt, request.personCount(), request.employeeId(),
                request.workstationId(), lines, request.designated(), trimToNull(request.note()),
                request.version(), currentUserId(username)));
    }

    public AppointmentDetail transition(
            long id, AppointmentStatus target, AppointmentTransitionRequest request, String username) {
        AppointmentDetail current = detail(id);
        AppointmentStatus from = AppointmentStatus.valueOf(current.appointment().status());
        if (!TRANSITIONS.getOrDefault(from, Set.of()).contains(target)) {
            throw new IllegalArgumentException("预约不能从" + from + "变更为" + target);
        }
        String reasonCode = trimToNull(request.reasonCode());
        String note = trimToNull(request.note());
        if (target == AppointmentStatus.CANCELLED || target == AppointmentStatus.NO_SHOW) {
            if (reasonCode == null) {
                throw new IllegalArgumentException("取消或爽约必须选择原因");
            }
            CancelReasonOption reason = CANCEL_REASONS.stream()
                    .filter(item -> item.code().equals(reasonCode)).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("取消原因无效"));
            if (reason.requiresNote() && note == null) {
                throw new IllegalArgumentException("所选原因必须填写说明");
            }
        }
        return repository.transition(new AppointmentStatusChange(
                id, from.name(), target.name(), request.version(), reasonCode, note,
                target == AppointmentStatus.ARRIVED ? request.personCount() : null,
                currentUserId(username)));
    }

    public List<CancelReasonOption> cancelReasons() {
        return CANCEL_REASONS;
    }

    private void validateCustomer(Long memberId, String guestName, String guestMobile) {
        if (memberId != null) {
            MemberDetail member = members.findById(memberId)
                    .orElseThrow(() -> new IllegalArgumentException("所选会员不存在"));
            if (!"ACTIVE".equals(member.status())) {
                throw new IllegalArgumentException("所选会员当前不可预约");
            }
            return;
        }
        if (trimToNull(guestName) == null || normalizeMobile(guestMobile) == null) {
            throw new IllegalArgumentException("散客预约必须填写姓名和手机号");
        }
    }

    private List<AppointmentServiceLine> resolveServices(long storeId, List<Long> requestedIds) {
        List<Long> ids = new LinkedHashSet<>(requestedIds).stream().toList();
        Map<Long, ServiceItemSummary> available = masterData.services(storeId, null).stream()
                .filter(service -> "ACTIVE".equals(service.status()) && "ON_SALE".equals(service.saleStatus()))
                .collect(java.util.stream.Collectors.toMap(ServiceItemSummary::id, service -> service));
        List<AppointmentServiceLine> lines = new ArrayList<>();
        for (int index = 0; index < ids.size(); index++) {
            ServiceItemSummary service = available.get(ids.get(index));
            if (service == null) {
                throw new IllegalArgumentException("预约项目不存在、已停用或未在本店上架");
            }
            lines.add(new AppointmentServiceLine(
                    service.id(), service.name(), service.durationMinutes(), service.storePrice(), index + 1));
        }
        return lines;
    }

    private LocalDateTime calculateEnd(LocalDateTime startAt, List<AppointmentServiceLine> lines) {
        if (startAt == null) throw new IllegalArgumentException("预约开始时间不能为空");
        int minutes = lines.stream().mapToInt(AppointmentServiceLine::durationMinutes).sum();
        LocalDateTime endAt = startAt.plusMinutes(minutes);
        if (!startAt.toLocalDate().equals(endAt.minusNanos(1).toLocalDate())) {
            throw new IllegalArgumentException("单次预约不能跨自然日");
        }
        if (startAt.toLocalTime().isBefore(LocalTime.of(6, 0)) || endAt.toLocalTime().isAfter(LocalTime.of(23, 0))) {
            throw new IllegalArgumentException("预约时间必须在06:00至23:00之间");
        }
        return endAt;
    }

    private void validateResource(long storeId, long employeeId, long workstationId) {
        EmployeeSummary employee = masterData.employees(storeId, null).stream()
                .filter(item -> item.id() == employeeId).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("所选技师不属于当前门店"));
        if (!employee.canService() || !"ACTIVE".equals(employee.status())) {
            throw new IllegalArgumentException("所选员工当前不可提供服务");
        }
        WorkstationSummary workstation = masterData.workstations(storeId).stream()
                .filter(item -> item.id() == workstationId).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("所选工位不属于当前门店"));
        if (!"ACTIVE".equals(workstation.status())) {
            throw new IllegalArgumentException("所选工位已停用");
        }
    }

    private void ensureNoConflict(
            long storeId, long employeeId, long workstationId,
            LocalDateTime startAt, LocalDateTime endAt, Long excludeId) {
        if (repository.hasConflict(storeId, employeeId, workstationId, startAt, endAt, excludeId)) {
            throw new DuplicateResourceException("所选技师或工位在该时段已被预约");
        }
    }

    private void validateStore(long storeId) {
        boolean exists = accessCatalog.stores().stream()
                .anyMatch(store -> store.id() == storeId && "ACTIVE".equals(store.status()));
        if (!exists) throw new IllegalArgumentException("所选门店不存在或已停用");
    }

    private long currentUserId(String username) {
        return accessCatalog.userIdentity(username).id();
    }

    private String normalizeStatus(String status) {
        if (trimToNull(status) == null) return null;
        try {
            return AppointmentStatus.valueOf(status.trim().toUpperCase(Locale.ROOT)).name();
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("预约状态无效");
        }
    }

    private String normalizeChoice(String value, String fallback, Set<String> allowed, String label) {
        String normalized = trimToNull(value) == null ? fallback : value.trim().toUpperCase(Locale.ROOT);
        if (!allowed.contains(normalized)) throw new IllegalArgumentException(label + "无效");
        return normalized;
    }

    private String normalizeMobile(String mobile) {
        String normalized = trimToNull(mobile);
        if (normalized == null) return null;
        normalized = normalized.replaceAll("[\\s-]", "");
        if (!normalized.matches("1[3-9]\\d{9}")) {
            throw new IllegalArgumentException("预约手机号格式不正确");
        }
        return normalized;
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
