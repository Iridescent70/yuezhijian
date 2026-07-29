package com.yuezhijian.server.trade;

import com.yuezhijian.server.appointment.AppointmentDetail;
import com.yuezhijian.server.appointment.AppointmentRepository;
import com.yuezhijian.server.appointment.AppointmentServiceLine;
import com.yuezhijian.server.common.ResourceNotFoundException;
import com.yuezhijian.server.iam.AccessCatalogService;
import com.yuezhijian.server.masterdata.EmployeeSummary;
import com.yuezhijian.server.masterdata.MasterDataRepository;
import com.yuezhijian.server.masterdata.ServiceItemSummary;
import com.yuezhijian.server.member.MemberDetail;
import com.yuezhijian.server.member.MemberRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TradeService {
    private static final Set<String> MUTABLE_BILL_STATUSES = Set.of("DRAFT", "PENDING_PAYMENT");

    private final TradeRepository repository;
    private final AppointmentRepository appointments;
    private final MasterDataRepository masterData;
    private final MemberRepository members;
    private final AccessCatalogService accessCatalog;
    private final TradeNumberGenerator numbers;

    public TradeService(
            TradeRepository repository,
            AppointmentRepository appointments,
            MasterDataRepository masterData,
            MemberRepository members,
            AccessCatalogService accessCatalog,
            TradeNumberGenerator numbers) {
        this.repository = repository;
        this.appointments = appointments;
        this.masterData = masterData;
        this.members = members;
        this.accessCatalog = accessCatalog;
        this.numbers = numbers;
    }

    public List<BillSummary> search(
            Long storeId, LocalDate startDate, LocalDate endDate, String status, String keyword) {
        long resolvedStoreId = storeId == null ? accessCatalog.stores().getFirst().id() : storeId;
        validateStore(resolvedStoreId);
        LocalDate start = startDate == null ? LocalDate.now() : startDate;
        LocalDate end = endDate == null ? start : endDate;
        if (end.isBefore(start) || ChronoUnit.DAYS.between(start, end) > 31) {
            throw new IllegalArgumentException("账单查询日期范围必须在1至32天内");
        }
        String normalizedStatus = normalizeStatus(status);
        return repository.search(new BillQuery(resolvedStoreId, start, end, normalizedStatus, trimToNull(keyword)));
    }

    public BillDetail detail(long id) {
        return repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("账单不存在"));
    }

    public List<PaymentMethodOption> paymentMethods(long storeId) {
        validateStore(storeId);
        return repository.paymentMethods(storeId);
    }

    public CreatedBill create(CreateBillRequest request, String username) {
        String key = trimToNull(request.idempotencyKey());
        Optional<CreatedBill> existing = repository.findByIdempotencyKey(key);
        if (existing.isPresent()) return existing.get();
        validateStore(request.storeId());
        validateCustomer(request.memberId(), request.guestName(), request.guestMobile());
        return repository.create(new BillDraft(
                numbers.billNo(), null, request.memberId(), trimToNull(request.guestName()),
                normalizeMobile(request.guestMobile()), null, request.storeId(),
                normalizeSource(request.sourceType()), request.personCount(), trimToNull(request.note()), key,
                List.of(), currentUserId(username)));
    }

    @Transactional
    public CreatedBill createFromAppointment(
            long appointmentId, CreateBillFromAppointmentRequest request, String username) {
        Optional<BillSummary> existing = repository.findByAppointmentId(appointmentId);
        if (existing.isPresent()) {
            BillSummary bill = existing.get();
            return new CreatedBill(bill.id(), bill.billNo(), bill.status(), bill.version());
        }
        AppointmentDetail appointment = appointments.findById(appointmentId)
                .orElseThrow(() -> new ResourceNotFoundException("预约不存在"));
        if (!Set.of("ARRIVED", "SERVING", "COMPLETED").contains(appointment.appointment().status())) {
            throw new IllegalArgumentException("预约到店后才能转为账单");
        }
        List<BillLineDraft> lines = request.copyServices() ? appointmentLines(appointment) : List.of();
        long operatorId = currentUserId(username);
        CreatedBill bill = repository.create(new BillDraft(
                numbers.billNo(), appointmentId, appointment.appointment().memberId(),
                appointment.appointment().memberId() == null ? appointment.appointment().customerName() : null,
                null, appointment.appointment().maskedMobile(), appointment.appointment().storeId(),
                "APPOINTMENT", appointment.appointment().personCount(),
                appointment.appointment().note(), "appointment:" + appointmentId, lines, operatorId));
        appointments.linkBill(appointmentId, bill.id(), operatorId);
        return bill;
    }

    public BillDetail addLine(long billId, AddBillLineRequest request, String username) {
        BillDetail bill = detail(billId);
        requireMutable(bill.bill());
        ServiceItemSummary service = masterData.services(bill.bill().storeId(), null).stream()
                .filter(item -> item.id() == request.serviceId() && "ACTIVE".equals(item.status())
                        && "ON_SALE".equals(item.saleStatus()))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("服务项目未在本店上架"));
        EmployeeSummary employee = null;
        if (request.employeeId() != null) {
            employee = masterData.employees(bill.bill().storeId(), null).stream()
                    .filter(item -> item.id() == request.employeeId() && item.canService()
                            && "ACTIVE".equals(item.status()))
                    .findFirst().orElseThrow(() -> new IllegalArgumentException("所选技师当前不可开单"));
        }
        return repository.addLine(new AddBillLineCommand(
                billId,
                new BillLineDraft(
                        "SERVICE", service.id(), service.code(), service.name(), money(service.storePrice()),
                        quantity(request.quantity()), employee == null ? null : employee.id(),
                        employee == null ? null : employee.name(), trimToNull(request.note())),
                request.version(), currentUserId(username)));
    }

    public SettlementQuote quote(long billId, SettlementQuoteRequest request, String username) {
        BillDetail bill = detail(billId);
        requireMutable(bill.bill());
        if (bill.lines().isEmpty() || bill.bill().receivableAmount().signum() <= 0) {
            throw new IllegalArgumentException("账单没有可结算的消费明细");
        }
        Map<Long, PaymentMethodOption> methods = repository.paymentMethods(bill.bill().storeId()).stream()
                .collect(Collectors.toMap(PaymentMethodOption::id, Function.identity()));
        List<QuotePayment> payments = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        BigDecimal cashTotal = BigDecimal.ZERO;
        for (SettlementPaymentRequest item : request.payments()) {
            PaymentMethodOption method = methods.get(item.paymentMethodId());
            if (method == null) throw new IllegalArgumentException("支付方式未在当前门店启用");
            BigDecimal amount = money(item.amount());
            String externalReference = trimToNull(item.externalReference());
            if (method.needsExternalReference() && externalReference == null) {
                throw new IllegalArgumentException(method.name() + "必须填写外部凭证号");
            }
            payments.add(new QuotePayment(method.id(), method.code(), method.name(), amount, externalReference));
            total = total.add(amount);
            if ("CASH".equals(method.type())) cashTotal = cashTotal.add(amount);
        }
        BigDecimal receivable = money(bill.bill().receivableAmount());
        BigDecimal change = total.subtract(receivable).max(BigDecimal.ZERO).setScale(4, RoundingMode.HALF_UP);
        BigDecimal difference = receivable.subtract(total).max(BigDecimal.ZERO).setScale(4, RoundingMode.HALF_UP);
        if (change.compareTo(cashTotal) > 0) {
            throw new IllegalArgumentException("非现金支付不能产生找零");
        }
        return repository.createQuote(new SettlementQuoteDraft(
                numbers.quoteNo(), billId, bill.bill().version(), receivable, total, change, difference,
                payments, LocalDateTime.now().plusMinutes(10), currentUserId(username)));
    }

    public BillDetail settle(long billId, SettleBillRequest request, String username) {
        SettlementQuote quote = repository.findQuote(request.quoteNo())
                .orElseThrow(() -> new IllegalArgumentException("结算试算不存在"));
        if (quote.billId() != billId) throw new IllegalArgumentException("结算试算与账单不匹配");
        return repository.settle(new SettleBillCommand(
                billId, quote, request.idempotencyKey(), currentUserId(username)));
    }

    public BillDetail voidBill(long billId, VoidBillRequest request, String username) {
        requireMutable(detail(billId).bill());
        return repository.voidBill(
                billId, request.reasonCode(), trimToNull(request.note()), request.version(), currentUserId(username));
    }

    private List<BillLineDraft> appointmentLines(AppointmentDetail appointment) {
        Map<Long, ServiceItemSummary> services = masterData.services(appointment.appointment().storeId(), null).stream()
                .collect(Collectors.toMap(ServiceItemSummary::id, Function.identity()));
        List<BillLineDraft> result = new ArrayList<>();
        for (AppointmentServiceLine line : appointment.services()) {
            ServiceItemSummary service = services.get(line.serviceId());
            result.add(new BillLineDraft(
                    "SERVICE", line.serviceId(), service == null ? "SVC-" + line.serviceId() : service.code(),
                    line.serviceName(), money(line.price()), BigDecimal.ONE,
                    appointment.appointment().employeeId(), appointment.appointment().employeeName(), null));
        }
        return result;
    }

    private void validateCustomer(Long memberId, String guestName, String guestMobile) {
        if (memberId != null) {
            MemberDetail member = members.findById(memberId)
                    .orElseThrow(() -> new IllegalArgumentException("所选会员不存在"));
            if (!"ACTIVE".equals(member.status())) throw new IllegalArgumentException("所选会员当前不可开单");
            return;
        }
        if (trimToNull(guestName) == null || normalizeMobile(guestMobile) == null) {
            throw new IllegalArgumentException("散客开单必须填写姓名和手机号");
        }
    }

    private void requireMutable(BillSummary bill) {
        if (!MUTABLE_BILL_STATUSES.contains(bill.status())) {
            throw new IllegalArgumentException("当前账单状态不允许修改或结算");
        }
    }

    private String normalizeStatus(String status) {
        if (trimToNull(status) == null) return null;
        try { return BillStatus.valueOf(status.trim().toUpperCase(Locale.ROOT)).name(); }
        catch (IllegalArgumentException exception) { throw new IllegalArgumentException("账单状态无效"); }
    }

    private String normalizeSource(String source) {
        String normalized = trimToNull(source) == null ? "PC" : source.trim().toUpperCase(Locale.ROOT);
        if (!Set.of("PC", "MOBILE", "HOME_SERVICE", "IMPORT").contains(normalized)) {
            throw new IllegalArgumentException("账单来源无效");
        }
        return normalized;
    }

    private void validateStore(long storeId) {
        boolean valid = accessCatalog.stores().stream()
                .anyMatch(store -> store.id() == storeId && "ACTIVE".equals(store.status()));
        if (!valid) throw new IllegalArgumentException("所选门店不存在或已停用");
    }

    private long currentUserId(String username) { return accessCatalog.userIdentity(username).id(); }

    private BigDecimal money(BigDecimal value) { return value.setScale(4, RoundingMode.HALF_UP); }

    private BigDecimal quantity(BigDecimal value) {
        BigDecimal normalized = value.setScale(4, RoundingMode.HALF_UP);
        if (normalized.signum() <= 0) throw new IllegalArgumentException("数量必须大于0");
        return normalized;
    }

    private String normalizeMobile(String mobile) {
        String normalized = trimToNull(mobile);
        if (normalized == null) return null;
        normalized = normalized.replaceAll("[\\s-]", "");
        if (!normalized.matches("1[3-9]\\d{9}")) throw new IllegalArgumentException("手机号格式不正确");
        return normalized;
    }

    private String trimToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
}
