package com.yuezhijian.server.trade;

import com.yuezhijian.server.common.DuplicateResourceException;
import com.yuezhijian.server.iam.AccessCatalogService;
import com.yuezhijian.server.member.MemberRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("memory")
public class MemoryTradeRepository implements TradeRepository {
    private static final List<PaymentMethodOption> METHODS = List.of(
            new PaymentMethodOption(1L, "CASH", "现金", "CASH", false, true, false, 10),
            new PaymentMethodOption(2L, "BANK_CARD", "银行卡", "BANK_CARD", true, true, false, 20),
            new PaymentMethodOption(3L, "WECHAT", "微信支付", "WECHAT", true, true, true, 30),
            new PaymentMethodOption(4L, "ALIPAY", "支付宝", "ALIPAY", true, true, true, 40),
            new PaymentMethodOption(5L, "MEITUAN", "美团核销", "MEITUAN", true, true, true, 50));

    private final Map<Long, BillDetail> bills = new LinkedHashMap<>();
    private final Map<String, Long> billIdempotency = new LinkedHashMap<>();
    private final Map<String, SettlementQuote> quotes = new LinkedHashMap<>();
    private final Map<String, Long> settlementIdempotency = new LinkedHashMap<>();
    private final AtomicLong billIds = new AtomicLong(2000);
    private final AtomicLong lineIds = new AtomicLong(3000);
    private final AtomicLong paymentIds = new AtomicLong(4000);
    private final AtomicLong historyIds = new AtomicLong(5000);
    private final MemberRepository members;
    private final AccessCatalogService accessCatalog;
    private final TradeNumberGenerator numbers;

    public MemoryTradeRepository(
            MemberRepository members, AccessCatalogService accessCatalog, TradeNumberGenerator numbers) {
        this.members = members;
        this.accessCatalog = accessCatalog;
        this.numbers = numbers;
    }

    @Override
    public List<PaymentMethodOption> paymentMethods(long storeId) {
        return METHODS;
    }

    @Override
    public synchronized List<BillSummary> search(BillQuery query) {
        LocalDateTime from = query.startDate().atStartOfDay();
        LocalDateTime until = query.endDate().plusDays(1).atStartOfDay();
        String keyword = query.keyword() == null ? null : query.keyword().toLowerCase();
        return bills.values().stream().map(BillDetail::bill)
                .filter(item -> item.storeId() == query.storeId())
                .filter(item -> !item.createdAt().isBefore(from) && item.createdAt().isBefore(until))
                .filter(item -> query.status() == null || query.status().equals(item.status()))
                .filter(item -> keyword == null || item.billNo().toLowerCase().contains(keyword)
                        || item.customerName().toLowerCase().contains(keyword))
                .sorted(Comparator.comparing(BillSummary::createdAt).reversed())
                .toList();
    }

    @Override
    public synchronized Optional<BillDetail> findById(long id) {
        return Optional.ofNullable(bills.get(id));
    }

    @Override
    public synchronized Optional<BillSummary> findByAppointmentId(long appointmentId) {
        return bills.values().stream().map(BillDetail::bill)
                .filter(item -> java.util.Objects.equals(item.appointmentId(), appointmentId)).findFirst();
    }

    @Override
    public synchronized Optional<CreatedBill> findByIdempotencyKey(String key) {
        if (key == null || !billIdempotency.containsKey(key)) return Optional.empty();
        BillSummary bill = bills.get(billIdempotency.get(key)).bill();
        return Optional.of(new CreatedBill(bill.id(), bill.billNo(), bill.status(), bill.version()));
    }

    @Override
    public synchronized Optional<BillDetail> findBySettlementIdempotencyKey(String key) {
        Long id = key == null ? null : settlementIdempotency.get(key);
        return id == null ? Optional.empty() : Optional.ofNullable(bills.get(id));
    }

    @Override
    public synchronized CreatedBill create(BillDraft draft) {
        Optional<CreatedBill> existing = findByIdempotencyKey(draft.idempotencyKey());
        if (existing.isPresent()) return existing.get();
        if (draft.appointmentId() != null && findByAppointmentId(draft.appointmentId()).isPresent()) {
            BillSummary bill = findByAppointmentId(draft.appointmentId()).orElseThrow();
            return new CreatedBill(bill.id(), bill.billNo(), bill.status(), bill.version());
        }
        long id = billIds.incrementAndGet();
        List<BillLine> lines = new ArrayList<>();
        for (int index = 0; index < draft.lines().size(); index++) {
            lines.add(toLine(draft.lines().get(index), index + 1));
        }
        BigDecimal total = total(lines);
        String status = lines.isEmpty() ? BillStatus.DRAFT.name() : BillStatus.PENDING_PAYMENT.name();
        BillSummary summary = new BillSummary(
                id, draft.billNo(), draft.appointmentId(), draft.memberId(), customerName(draft), customerMobile(draft),
                draft.storeId(), storeName(draft.storeId()), draft.sourceType(), draft.personCount(), total,
                BigDecimal.ZERO, total, BigDecimal.ZERO, BigDecimal.ZERO, status, draft.note(), null,
                LocalDateTime.now(), "1");
        BillHistoryItem history = new BillHistoryItem(
                historyIds.incrementAndGet(), null, status, null, "创建账单", LocalDateTime.now(), draft.operatorId());
        bills.put(id, new BillDetail(summary, lines, List.of(), List.of(history)));
        if (draft.idempotencyKey() != null) billIdempotency.put(draft.idempotencyKey(), id);
        return new CreatedBill(id, summary.billNo(), status, summary.version());
    }

    @Override
    public synchronized BillDetail addLine(AddBillLineCommand command) {
        BillDetail current = requireVersion(command.billId(), command.version());
        requireMutable(current.bill());
        List<BillLine> lines = new ArrayList<>(current.lines());
        lines.add(toLine(command.line(), lines.size() + 1));
        BigDecimal total = total(lines);
        BillSummary bill = copyBill(
                current.bill(), total, total, current.bill().receivedAmount(), current.bill().changeAmount(),
                BillStatus.PENDING_PAYMENT.name(), nextVersion(current.bill().version()), current.bill().settledAt());
        BillDetail result = new BillDetail(bill, lines, current.payments(), current.history());
        bills.put(bill.id(), result);
        return result;
    }

    @Override
    public synchronized SettlementQuote createQuote(SettlementQuoteDraft draft) {
        SettlementQuote quote = new SettlementQuote(
                draft.quoteNo(), draft.billId(), draft.billVersion(), draft.receivableAmount(),
                draft.paymentTotal(), draft.assetAmount(), draft.externalPaymentAmount(), draft.changeAmount(),
                draft.differenceAmount(), draft.payments(), draft.assets(), draft.expiresAt(), false);
        quotes.put(quote.quoteNo(), quote);
        return quote;
    }

    @Override
    public synchronized Optional<SettlementQuote> findQuote(String quoteNo) {
        return Optional.ofNullable(quotes.get(quoteNo));
    }

    @Override
    public synchronized BillDetail settle(SettleBillCommand command) {
        if (settlementIdempotency.containsKey(command.idempotencyKey())) {
            return bills.get(settlementIdempotency.get(command.idempotencyKey()));
        }
        BillDetail current = requireVersion(command.billId(), command.quote().billVersion());
        requireMutable(current.bill());
        if (command.quote().used() || command.quote().expiresAt().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("结算试算已失效，请重新试算");
        }
        if (command.quote().differenceAmount().signum() != 0) {
            throw new IllegalArgumentException("支付金额尚未覆盖账单应收");
        }
        List<BillPayment> payments = new ArrayList<>();
        for (QuotePayment item : command.quote().payments()) {
            payments.add(new BillPayment(
                    paymentIds.incrementAndGet(), numbers.paymentNo(), item.paymentMethodId(),
                    item.paymentMethodName(), item.amount(), "SUCCESS", item.externalReference(), LocalDateTime.now()));
        }
        BillSummary bill = copyBill(
                current.bill(), current.bill().originalAmount(), current.bill().receivableAmount(),
                current.bill().receivableAmount(), command.quote().changeAmount(), BillStatus.SETTLED.name(),
                nextVersion(current.bill().version()), LocalDateTime.now());
        List<BillHistoryItem> history = new ArrayList<>(current.history());
        history.add(new BillHistoryItem(
                historyIds.incrementAndGet(), current.bill().status(), BillStatus.SETTLED.name(), null,
                "收银结算", LocalDateTime.now(), command.operatorId()));
        List<BillLine> settledLines = current.lines().stream().map(line -> new BillLine(
                line.id(), line.lineNo(), line.itemType(), line.itemId(), line.itemCode(), line.itemName(),
                line.unitPrice(), line.quantity(), line.originalAmount(), line.discountAmount(),
                line.receivableAmount(), line.receivableAmount(), line.employeeId(), line.employeeName(), line.note()
        )).toList();
        BillDetail result = new BillDetail(bill, settledLines, payments, history);
        bills.put(bill.id(), result);
        quotes.put(command.quote().quoteNo(), new SettlementQuote(
                command.quote().quoteNo(), command.quote().billId(), command.quote().billVersion(),
                command.quote().receivableAmount(), command.quote().paymentTotal(), command.quote().assetAmount(),
                command.quote().externalPaymentAmount(), command.quote().changeAmount(),
                command.quote().differenceAmount(), command.quote().payments(), command.quote().assets(),
                command.quote().expiresAt(), true));
        settlementIdempotency.put(command.idempotencyKey(), bill.id());
        return result;
    }

    @Override
    public synchronized BillDetail voidBill(
            long billId, String reasonCode, String note, String version, long operatorId) {
        BillDetail current = requireVersion(billId, version);
        requireMutable(current.bill());
        BillSummary bill = copyBill(
                current.bill(), current.bill().originalAmount(), current.bill().receivableAmount(),
                current.bill().receivedAmount(), current.bill().changeAmount(), BillStatus.VOIDED.name(),
                nextVersion(version), current.bill().settledAt());
        List<BillHistoryItem> history = new ArrayList<>(current.history());
        history.add(new BillHistoryItem(
                historyIds.incrementAndGet(), current.bill().status(), BillStatus.VOIDED.name(), reasonCode,
                note, LocalDateTime.now(), operatorId));
        BillDetail result = new BillDetail(bill, current.lines(), current.payments(), history);
        bills.put(billId, result);
        return result;
    }

    private BillLine toLine(BillLineDraft draft, int lineNo) {
        BigDecimal amount = draft.amount();
        return new BillLine(
                lineIds.incrementAndGet(), lineNo, draft.itemType(), draft.itemId(), draft.itemCode(),
                draft.itemName(), draft.unitPrice(), draft.quantity(), amount, BigDecimal.ZERO, amount,
                BigDecimal.ZERO, draft.employeeId(), draft.employeeName(), draft.note());
    }

    private BillDetail requireVersion(long id, String version) {
        BillDetail current = bills.get(id);
        if (current == null) throw new IllegalArgumentException("账单不存在");
        if (!current.bill().version().equals(version)) {
            throw new DuplicateResourceException("账单已被他人修改，请刷新后重试");
        }
        return current;
    }

    private void requireMutable(BillSummary bill) {
        if (!List.of(BillStatus.DRAFT.name(), BillStatus.PENDING_PAYMENT.name()).contains(bill.status())) {
            throw new IllegalArgumentException("当前账单状态不允许修改或结算");
        }
    }

    private BigDecimal total(List<BillLine> lines) {
        return lines.stream().map(BillLine::receivableAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private String customerName(BillDraft draft) {
        return draft.memberId() == null ? draft.guestName()
                : members.findById(draft.memberId()).orElseThrow().fullName();
    }

    private String customerMobile(BillDraft draft) {
        if (draft.memberId() != null) return members.findById(draft.memberId()).orElseThrow().maskedMobile();
        return draft.guestMaskedMobile() != null ? draft.guestMaskedMobile() : draft.guestMobile() == null ? null
                : "*******" + draft.guestMobile().substring(draft.guestMobile().length() - 4);
    }

    private String storeName(long storeId) {
        return accessCatalog.stores().stream().filter(item -> item.id() == storeId)
                .map(item -> item.name()).findFirst().orElse("未知门店");
    }

    private String nextVersion(String version) { return Long.toString(Long.parseLong(version) + 1); }

    private BillSummary copyBill(
            BillSummary old, BigDecimal original, BigDecimal receivable, BigDecimal received,
            BigDecimal change, String status, String version, LocalDateTime settledAt) {
        return new BillSummary(
                old.id(), old.billNo(), old.appointmentId(), old.memberId(), old.customerName(), old.maskedMobile(),
                old.storeId(), old.storeName(), old.sourceType(), old.personCount(), original,
                original.subtract(receivable), receivable, received, change, status, old.note(), settledAt,
                old.createdAt(), version);
    }
}
