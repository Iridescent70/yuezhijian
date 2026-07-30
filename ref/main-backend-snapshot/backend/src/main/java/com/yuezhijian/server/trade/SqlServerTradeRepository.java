package com.yuezhijian.server.trade;

import com.yuezhijian.server.common.DuplicateResourceException;
import com.yuezhijian.server.common.SensitiveDataCodec;
import com.yuezhijian.server.payment.PaymentMethodRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Profile("sqlserver")
public class SqlServerTradeRepository implements TradeRepository {
    private final TradeMapper mapper;
    private final ReversalMapper reversals;
    private final SensitiveDataCodec codec;
    private final TradeNumberGenerator numbers;
    private final PaymentMethodRepository paymentMethodRepository;

    public SqlServerTradeRepository(
            TradeMapper mapper,
            ReversalMapper reversals,
            SensitiveDataCodec codec,
            TradeNumberGenerator numbers,
            PaymentMethodRepository paymentMethodRepository) {
        this.mapper = mapper;
        this.reversals = reversals;
        this.codec = codec;
        this.numbers = numbers;
        this.paymentMethodRepository = paymentMethodRepository;
    }

    @Override
    public List<PaymentMethodOption> paymentMethods(long storeId) {
        return paymentMethodRepository.options(storeId);
    }

    @Override
    public List<BillSummary> search(BillQuery query) {
        return mapper.search(query, query.startDate().atStartOfDay(), query.endDate().plusDays(1).atStartOfDay());
    }

    @Override
    public Optional<BillDetail> findById(long id) {
        BillSummary bill = mapper.findSummaryById(id);
        return bill == null ? Optional.empty()
                : Optional.of(new BillDetail(
                        bill, mapper.findLines(id), mapper.findPayments(id), mapper.findDiscounts(id),
                        mapper.findAssetUsages(id), mapper.findHistory(id)));
    }

    @Override
    public Optional<BillSummary> findByAppointmentId(long appointmentId) {
        return Optional.ofNullable(mapper.findByAppointmentId(appointmentId));
    }

    @Override
    public Optional<CreatedBill> findByIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null) return Optional.empty();
        BillSummary bill = mapper.findByIdempotencyKey(idempotencyKey);
        return bill == null ? Optional.empty()
                : Optional.of(new CreatedBill(bill.id(), bill.billNo(), bill.status(), bill.version()));
    }

    @Override
    public Optional<BillDetail> findBySettlementIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null) return Optional.empty();
        BillSummary bill = mapper.findBySettlementIdempotency(idempotencyKey);
        return bill == null ? Optional.empty() : findById(bill.id());
    }

    @Override
    @Transactional
    public CreatedBill create(BillDraft draft) {
        Optional<CreatedBill> existing = findByIdempotencyKey(draft.idempotencyKey());
        if (existing.isPresent()) return existing.get();
        if (draft.appointmentId() != null) {
            Optional<BillSummary> appointmentBill = findByAppointmentId(draft.appointmentId());
            if (appointmentBill.isPresent()) {
                BillSummary bill = appointmentBill.get();
                return new CreatedBill(bill.id(), bill.billNo(), bill.status(), bill.version());
            }
        }
        BigDecimal total = draft.lines().stream()
                .map(BillLineDraft::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        String status = draft.lines().isEmpty() ? BillStatus.DRAFT.name() : BillStatus.PENDING_PAYMENT.name();
        String ciphertext = draft.guestMobile() == null ? null : codec.encrypt(draft.guestMobile());
        String hash = draft.guestMobile() == null ? null : codec.searchableHash(draft.guestMobile());
        String last4 = draft.guestMobile() == null ? null
                : draft.guestMobile().substring(draft.guestMobile().length() - 4);
        long id = mapper.insertBill(new ProtectedBillRow(
                draft.billNo(), draft.appointmentId(), draft.memberId(), draft.guestName(), ciphertext, hash, last4,
                draft.storeId(), draft.sourceType(), draft.personCount(), total, total, status,
                draft.note(), draft.idempotencyKey(), draft.operatorId()));
        for (int index = 0; index < draft.lines().size(); index++) {
            insertLine(id, index + 1, draft.storeId(), draft.lines().get(index));
        }
        mapper.insertHistory(id, null, status, null, "创建账单", draft.operatorId());
        BillSummary saved = mapper.findSummaryById(id);
        return new CreatedBill(id, saved.billNo(), saved.status(), saved.version());
    }

    @Override
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public BillDetail addLine(AddBillLineCommand command) {
        BillDetail current = requireDetail(command.billId());
        BigDecimal total = current.lines().stream().map(BillLine::originalAmount)
                .reduce(command.line().amount(), BigDecimal::add);
        if (mapper.updateTotals(command.billId(), total, command.version(), command.operatorId()) != 1) {
            throw new DuplicateResourceException("账单已被他人修改，请刷新后重试");
        }
        mapper.deactivateDiscounts(command.billId());
        mapper.resetLineDiscounts(command.billId());
        mapper.resetEmployeePerformance(command.billId());
        insertLine(
                command.billId(), mapper.nextLineNo(command.billId()), current.bill().storeId(), command.line());
        return requireDetail(command.billId());
    }

    @Override
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public BillDetail updateLine(UpdateBillLineCommand command) {
        BillDetail current = requireDetail(command.billId());
        BillLine existing = current.lines().stream().filter(line -> line.id() == command.lineId()).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("账单项目不存在"));
        BigDecimal total = current.lines().stream().map(BillLine::originalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .subtract(existing.originalAmount()).add(command.line().amount());
        if (mapper.updateTotals(command.billId(), total, command.version(), command.operatorId()) != 1) {
            throw new DuplicateResourceException("账单已被他人修改，请刷新后重试");
        }
        mapper.deactivateDiscounts(command.billId());
        mapper.resetLineDiscounts(command.billId());
        mapper.resetEmployeePerformance(command.billId());
        if (mapper.updateLine(command.billId(), command.lineId(), command.line()) != 1) {
            throw new IllegalArgumentException("账单项目不存在");
        }
        mapper.deleteLineEmployees(command.lineId());
        if (command.line().employeeId() != null) {
            mapper.insertLineEmployee(
                    command.lineId(), command.line().employeeId(), current.bill().storeId(), command.line().amount());
        }
        return requireDetail(command.billId());
    }

    @Override
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public BillDetail removeLine(RemoveBillLineCommand command) {
        BillDetail current = requireDetail(command.billId());
        BillLine existing = current.lines().stream().filter(line -> line.id() == command.lineId()).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("账单项目不存在"));
        BigDecimal total = current.lines().stream().map(BillLine::originalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add).subtract(existing.originalAmount());
        if (mapper.updateTotals(command.billId(), total, command.version(), command.operatorId()) != 1) {
            throw new DuplicateResourceException("账单已被他人修改，请刷新后重试");
        }
        mapper.deactivateDiscounts(command.billId());
        mapper.resetLineDiscounts(command.billId());
        mapper.resetEmployeePerformance(command.billId());
        if (mapper.removeLine(command.billId(), command.lineId(), command.operatorId()) != 1) {
            throw new IllegalArgumentException("账单项目不存在");
        }
        return requireDetail(command.billId());
    }

    @Override
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public BillDetail applyDiscount(BillDiscountDraft draft) {
        BigDecimal receivable = draft.originalAmount().subtract(draft.discountAmount());
        if (mapper.updateDiscountTotals(draft, receivable) != 1) {
            throw new DuplicateResourceException("账单已被他人修改，请刷新后重试");
        }
        mapper.deactivateDiscounts(draft.billId());
        for (BillDiscountAllocation allocation : draft.allocations()) {
            if (mapper.applyLineDiscount(draft.billId(), allocation) != 1) {
                throw new DuplicateResourceException("账单项目已发生变化，请刷新后重试");
            }
            mapper.updateLinePerformance(allocation.billLineId(), allocation.receivableAmount());
            if (allocation.discountAmount().signum() > 0) mapper.insertDiscount(draft, allocation);
        }
        return requireDetail(draft.billId());
    }

    @Override
    @Transactional
    public SettlementQuote createQuote(SettlementQuoteDraft draft) {
        long id = mapper.insertQuote(draft);
        for (int index = 0; index < draft.payments().size(); index++) {
            mapper.insertQuotePayment(id, draft.payments().get(index), index + 1);
        }
        for (int index = 0; index < draft.assets().size(); index++) {
            mapper.insertQuoteAsset(id, draft.assets().get(index), index + 1);
        }
        return requireQuote(draft.quoteNo());
    }

    @Override
    public Optional<SettlementQuote> findQuote(String quoteNo) {
        QuoteRow row = mapper.findQuote(quoteNo);
        if (row == null) return Optional.empty();
        return Optional.of(new SettlementQuote(
                row.quoteNo(), row.billId(), row.billVersion(), row.receivableAmount(), row.paymentTotal(),
                row.assetAmount(), row.externalPaymentAmount(), row.changeAmount(), row.differenceAmount(),
                mapper.findQuotePayments(row.id()), mapper.findQuoteAssets(row.id()), row.expiresAt(), row.used()));
    }

    @Override
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public BillDetail settle(SettleBillCommand command) {
        BillSummary existing = mapper.findBySettlementIdempotency(command.idempotencyKey());
        if (existing != null) return requireDetail(existing.id());
        SettlementQuote quote = requireQuote(command.quote().quoteNo());
        if (quote.used() || quote.expiresAt().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("结算试算已失效，请重新试算");
        }
        if (quote.differenceAmount().signum() != 0) {
            throw new IllegalArgumentException("支付金额尚未覆盖账单应收");
        }
        if (mapper.markQuoteUsed(quote.quoteNo()) != 1) {
            throw new DuplicateResourceException("结算试算已被使用，请刷新账单");
        }
        if (mapper.settleBill(
                command.billId(), quote.billVersion(), quote.changeAmount(),
                command.idempotencyKey(), command.operatorId()) != 1) {
            throw new DuplicateResourceException("账单已被他人修改，请重新试算");
        }
        for (int index = 0; index < quote.payments().size(); index++) {
            mapper.insertPayment(
                    numbers.paymentNo(), command.billId(), quote.payments().get(index),
                    command.idempotencyKey() + ':' + index, command.operatorId());
        }
        mapper.markLinesSettled(command.billId());
        mapper.insertHistory(
                command.billId(), BillStatus.PENDING_PAYMENT.name(), BillStatus.SETTLED.name(),
                null, "收银结算", command.operatorId());
        return requireDetail(command.billId());
    }

    @Override
    @Transactional
    public BillDetail voidBill(
            long billId, String reasonCode, String note, String version, long operatorId) {
        BillDetail current = requireDetail(billId);
        if (mapper.voidBill(billId, reasonCode, version, operatorId) != 1) {
            throw new DuplicateResourceException("账单已被他人修改，请刷新后重试");
        }
        mapper.insertHistory(
                billId, current.bill().status(), BillStatus.VOIDED.name(), reasonCode, note, operatorId);
        return requireDetail(billId);
    }

    @Override
    public List<ReversalSummary> reversals(String status) {
        return reversals.search(status);
    }

    @Override
    public Optional<ReversalDetail> findReversal(long id) {
        return Optional.ofNullable(reversals.findById(id)).map(this::toReversalDetail);
    }

    @Override
    public Optional<ReversalDetail> findReversalByRequestKey(String key) {
        if (key == null) return Optional.empty();
        return Optional.ofNullable(reversals.findByRequestKey(key)).map(this::toReversalDetail);
    }

    @Override
    public Optional<ReversalDetail> findReversalByExecutionKey(String key) {
        if (key == null) return Optional.empty();
        return Optional.ofNullable(reversals.findByExecutionKey(key)).map(this::toReversalDetail);
    }

    @Override
    public Optional<ReversalDetail> findActiveReversalByBill(long billId) {
        return Optional.ofNullable(reversals.findActiveByBill(billId)).map(this::toReversalDetail);
    }

    @Override
    @Transactional
    public ReversalDetail createReversal(ReversalDraft draft) {
        Optional<ReversalDetail> existing = findReversalByRequestKey(draft.idempotencyKey());
        if (existing.isPresent()) return existing.get();
        long id = reversals.insert(draft);
        return requireReversal(id);
    }

    @Override
    @Transactional
    public ReversalDetail reviewReversal(
            long id, boolean approved, String comment, String version, long operatorId) {
        if (reversals.review(id, approved ? "APPROVED" : "REJECTED", comment, version, operatorId) != 1) {
            throw new DuplicateResourceException("冲销申请已被他人处理，请刷新后重试");
        }
        return requireReversal(id);
    }

    @Override
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public ReversalDetail executeReversal(ReversalExecutionCommand command) {
        Optional<ReversalDetail> existing = findReversalByExecutionKey(command.idempotencyKey());
        if (existing.isPresent()) return existing.get();
        ReversalDetail current = requireReversal(command.reversal().reversal().id());
        if (reversals.markExecuted(
                current.reversal().id(), command.version(), command.idempotencyKey(), command.operatorId()) != 1) {
            throw new DuplicateResourceException("冲销申请已被他人处理，请刷新后重试");
        }
        if (reversals.reverseBill(current.reversal().billId(), command.operatorId()) != 1) {
            throw new DuplicateResourceException("账单状态已发生变化，无法执行冲销");
        }
        for (int index = 0; index < current.payments().size(); index++) {
            ReversalPaymentImpact payment = current.payments().get(index);
            if (reversals.markPaymentRefunded(payment.paymentId()) != 1) {
                throw new DuplicateResourceException("支付状态已发生变化，无法执行冲销");
            }
            reversals.insertPaymentRefund(
                    numbers.refundNo(), current.reversal().id(), payment.paymentId(), payment.amount(),
                    command.idempotencyKey() + ':' + index, command.operatorId());
        }
        mapper.insertHistory(
                current.reversal().billId(), BillStatus.SETTLED.name(), BillStatus.REVERSED.name(),
                "FULL_REVERSAL", "整单冲销：" + current.reversal().reason(), command.operatorId());
        return requireReversal(current.reversal().id());
    }

    private void insertLine(long billId, int lineNo, long storeId, BillLineDraft line) {
        long lineId = mapper.insertLine(billId, lineNo, line);
        if (line.employeeId() != null) {
            mapper.insertLineEmployee(lineId, line.employeeId(), storeId, line.amount());
        }
    }

    private BillDetail requireDetail(long id) {
        return findById(id).orElseThrow(() -> new IllegalArgumentException("账单不存在"));
    }

    private SettlementQuote requireQuote(String quoteNo) {
        return findQuote(quoteNo).orElseThrow(() -> new IllegalArgumentException("结算试算不存在"));
    }

    private ReversalDetail requireReversal(long id) {
        return findReversal(id).orElseThrow(() -> new IllegalArgumentException("冲销申请不存在"));
    }

    private ReversalDetail toReversalDetail(ReversalSummary summary) {
        BillDetail bill = requireDetail(summary.billId());
        List<ReversalPaymentImpact> payments = reversalPayments(bill);
        List<ReversalAssetImpact> assets = bill.assetUsages().stream()
                .map(asset -> new ReversalAssetImpact(
                        asset.id(), asset.assetType(), asset.memberId(), asset.voucherCodeId(), asset.memberCardId(),
                        asset.memberCardBalanceId(), asset.billLineId(), asset.serviceId(), asset.quantity(),
                        asset.amount(), asset.assetLedgerId(), asset.displayName()))
                .toList();
        return new ReversalDetail(summary, payments, assets);
    }

    private List<ReversalPaymentImpact> reversalPayments(BillDetail bill) {
        java.util.Map<Long, PaymentMethodOption> methods = paymentMethods(bill.bill().storeId()).stream()
                .collect(java.util.stream.Collectors.toMap(PaymentMethodOption::id, java.util.function.Function.identity()));
        BigDecimal remainingChange = bill.bill().changeAmount();
        List<ReversalPaymentImpact> result = new java.util.ArrayList<>();
        for (BillPayment payment : bill.payments()) {
            PaymentMethodOption method = methods.get(payment.paymentMethodId());
            if (method == null) throw new IllegalArgumentException("支付方式不存在");
            BigDecimal amount = payment.amount();
            if ("CASH".equals(method.type()) && remainingChange.signum() > 0) {
                BigDecimal deducted = amount.min(remainingChange);
                amount = amount.subtract(deducted);
                remainingChange = remainingChange.subtract(deducted);
            }
            if (amount.signum() > 0) {
                result.add(new ReversalPaymentImpact(
                        payment.id(), payment.paymentMethodName(), amount, payment.status()));
            }
        }
        if (remainingChange.signum() != 0) throw new IllegalArgumentException("账单找零记录无法匹配现金支付");
        return List.copyOf(result);
    }
}
