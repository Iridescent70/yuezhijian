package com.yuezhijian.server.trade;

import com.yuezhijian.server.appointment.AppointmentDetail;
import com.yuezhijian.server.appointment.AppointmentRepository;
import com.yuezhijian.server.appointment.AppointmentServiceLine;
import com.yuezhijian.server.asset.AssetRepository;
import com.yuezhijian.server.asset.BalanceAccount;
import com.yuezhijian.server.asset.BalanceSettlementConsumption;
import com.yuezhijian.server.asset.CardRepository;
import com.yuezhijian.server.asset.CardSettlementConsumption;
import com.yuezhijian.server.asset.MemberCardBalanceItem;
import com.yuezhijian.server.asset.MemberCardDetail;
import com.yuezhijian.server.asset.MemberCardSummary;
import com.yuezhijian.server.asset.PointAccount;
import com.yuezhijian.server.asset.PointSettlementConsumption;
import com.yuezhijian.server.common.DuplicateResourceException;
import com.yuezhijian.server.common.ResourceNotFoundException;
import com.yuezhijian.server.commission.CommissionService;
import com.yuezhijian.server.benefit.BenefitRepository;
import com.yuezhijian.server.benefit.VoucherCodeSummary;
import com.yuezhijian.server.benefit.VoucherSettlementConsumption;
import com.yuezhijian.server.benefit.VoucherSettlementOption;
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
import java.util.HashSet;
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
    private final AssetRepository assets;
    private final CardRepository cards;
    private final BenefitRepository benefits;
    private final CommissionService commissions;
    private final AccessCatalogService accessCatalog;
    private final TradeNumberGenerator numbers;

    public TradeService(
            TradeRepository repository,
            AppointmentRepository appointments,
            MasterDataRepository masterData,
            MemberRepository members,
            AssetRepository assets,
            CardRepository cards,
            BenefitRepository benefits,
            CommissionService commissions,
            AccessCatalogService accessCatalog,
            TradeNumberGenerator numbers) {
        this.repository = repository;
        this.appointments = appointments;
        this.masterData = masterData;
        this.members = members;
        this.assets = assets;
        this.cards = cards;
        this.benefits = benefits;
        this.commissions = commissions;
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

    public BillDetail updateLine(
            long billId, long lineId, UpdateBillLineRequest request, String username) {
        BillDetail bill = detail(billId);
        requireMutable(bill.bill());
        BillLine current = bill.lines().stream().filter(line -> line.id() == lineId).findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("账单项目不存在"));
        if (!"SERVICE".equals(current.itemType())) throw new IllegalArgumentException("当前项目类型暂不支持编辑");
        ServiceItemSummary service = masterData.services(bill.bill().storeId(), null).stream()
                .filter(item -> item.id() == current.itemId() && "ACTIVE".equals(item.status())
                        && "ON_SALE".equals(item.saleStatus()))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("服务项目未在本店上架"));
        EmployeeSummary employee = null;
        if (request.employeeId() != null) {
            employee = masterData.employees(bill.bill().storeId(), null).stream()
                    .filter(item -> item.id() == request.employeeId() && item.canService()
                            && "ACTIVE".equals(item.status()))
                    .findFirst().orElseThrow(() -> new IllegalArgumentException("所选技师当前不可开单"));
        }
        return repository.updateLine(new UpdateBillLineCommand(
                billId, lineId,
                new BillLineDraft(
                        current.itemType(), service.id(), service.code(), service.name(), money(service.storePrice()),
                        quantity(request.quantity()), employee == null ? null : employee.id(),
                        employee == null ? null : employee.name(), trimToNull(request.note())),
                request.version(), currentUserId(username)));
    }

    public BillDetail removeLine(long billId, long lineId, String version, String username) {
        BillDetail bill = detail(billId);
        requireMutable(bill.bill());
        if (trimToNull(version) == null) throw new IllegalArgumentException("账单版本不能为空");
        if (bill.lines().stream().noneMatch(line -> line.id() == lineId)) {
            throw new ResourceNotFoundException("账单项目不存在");
        }
        return repository.removeLine(new RemoveBillLineCommand(
                billId, lineId, version, currentUserId(username)));
    }

    public BillDetail applyDiscount(long billId, ApplyBillDiscountRequest request, String username) {
        BillDetail bill = detail(billId);
        requireMutable(bill.bill());
        if (bill.lines().isEmpty()) throw new IllegalArgumentException("账单没有可优惠的消费明细");
        String type = request.discountType().trim().toUpperCase(Locale.ROOT);
        if (!Set.of("AMOUNT", "RATE").contains(type)) throw new IllegalArgumentException("优惠类型无效");
        BigDecimal original = bill.lines().stream().map(BillLine::originalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(4, RoundingMode.HALF_UP);
        BigDecimal discountValue;
        BigDecimal discountAmount;
        if ("AMOUNT".equals(type)) {
            discountValue = money(request.value());
            discountAmount = discountValue;
        } else {
            discountValue = request.value().setScale(6, RoundingMode.HALF_UP);
            if (discountValue.signum() <= 0 || discountValue.compareTo(BigDecimal.ONE) > 0) {
                throw new IllegalArgumentException("折扣率必须大于0且不超过1");
            }
            discountAmount = money(original.multiply(BigDecimal.ONE.subtract(discountValue)));
        }
        if (discountAmount.compareTo(original) >= 0) {
            throw new IllegalArgumentException("优惠金额必须小于账单原价");
        }
        List<BillDiscountAllocation> allocations = allocateDiscount(bill.lines(), original, discountAmount);
        return repository.applyDiscount(new BillDiscountDraft(
                numbers.discountBatchNo(), billId, type, discountValue, original, discountAmount,
                request.reason().trim(), request.version(), allocations, currentUserId(username)));
    }

    public List<CardSettlementOption> cardOptions(long billId) {
        BillDetail bill = detail(billId);
        if (bill.bill().memberId() == null) return List.of();
        LocalDateTime now = LocalDateTime.now();
        List<MemberCardSummary> activeCards = cards.memberCards(bill.bill().memberId(), "ACTIVE").stream()
                .filter(card -> !card.expiresAt().isBefore(now))
                .toList();
        List<CardSettlementOption> result = new ArrayList<>();
        for (BillLine line : bill.lines()) {
            if (!"SERVICE".equals(line.itemType())) continue;
            List<CardSettlementOption> lineOptions = new ArrayList<>();
            for (MemberCardSummary card : activeCards) {
                MemberCardDetail detail = cards.findMemberCard(card.id()).orElseThrow();
                for (MemberCardBalanceItem balance : detail.balances()) {
                    if (balance.serviceId() != line.itemId()) continue;
                    BigDecimal required = balance.deductTimes().multiply(line.quantity()).setScale(4, RoundingMode.HALF_UP);
                    if (required.signum() <= 0 || balance.remainingTimes().compareTo(required) < 0) continue;
                    lineOptions.add(new CardSettlementOption(
                            line.id(), line.itemName(), card.id(), card.cardNo(), card.cardTypeName(), balance.id(),
                            balance.remainingTimes(), balance.deductTimes(), required, card.expiresAt(), false));
                }
            }
            lineOptions.sort(java.util.Comparator.comparing(CardSettlementOption::expiresAt)
                    .thenComparingLong(CardSettlementOption::memberCardId));
            for (int index = 0; index < lineOptions.size(); index++) {
                CardSettlementOption option = lineOptions.get(index);
                result.add(new CardSettlementOption(
                        option.billLineId(), option.billLineName(), option.memberCardId(), option.cardNo(),
                        option.cardTypeName(), option.memberCardBalanceId(), option.remainingTimes(),
                        option.deductTimes(), option.requiredTimes(), option.expiresAt(), index == 0));
            }
        }
        return List.copyOf(result);
    }

    public SettlementAssetOptions assetOptions(long billId) {
        BillDetail bill = detail(billId);
        if (bill.bill().memberId() == null) {
            return new SettlementAssetOptions(null, null, assets.pointsPerYuan(), List.of(), List.of());
        }
        long memberId = bill.bill().memberId();
        return new SettlementAssetOptions(
                assets.findBalanceAccount(memberId).orElse(null),
                assets.findPointAccount(memberId).orElse(null),
                assets.pointsPerYuan(),
                cardOptions(billId),
                voucherOptions(memberId, bill.bill().receivableAmount()));
    }

    private List<VoucherSettlementOption> voucherOptions(long memberId, BigDecimal receivable) {
        LocalDateTime now = LocalDateTime.now();
        return benefits.voucherCodes(memberId, "BOUND", null).stream()
                .filter(item -> !item.validFrom().isAfter(now) && !item.validUntil().isBefore(now))
                .filter(item -> receivable.compareTo(item.minSpend()) >= 0)
                .map(item -> new VoucherSettlementOption(
                        item.id(), item.code(), item.voucherName(), item.benefitType(), item.faceAmount(),
                        item.discountRate(), item.minSpend(), voucherAmount(item, money(receivable)),
                        item.validUntil(), item.version()))
                .filter(item -> item.previewAmount().signum() > 0)
                .toList();
    }

    public SettlementQuote quote(long billId, SettlementQuoteRequest request, String username) {
        BillDetail bill = detail(billId);
        requireMutable(bill.bill());
        if (bill.lines().isEmpty() || bill.bill().receivableAmount().signum() <= 0) {
            throw new IllegalArgumentException("账单没有可结算的消费明细");
        }
        Long memberId = bill.bill().memberId();
        if ((request.balanceAmount().signum() > 0 || request.points() > 0 || !request.cards().isEmpty()
                || !request.voucherCodeIds().isEmpty())
                && memberId == null) {
            throw new IllegalArgumentException("散客账单不能使用会员资产");
        }
        BigDecimal receivable = money(bill.bill().receivableAmount());
        List<SettlementAssetUsage> assetUsages = new ArrayList<>();
        BigDecimal assetTotal = BigDecimal.ZERO.setScale(4);

        Map<String, CardSettlementOption> cardOptionMap = cardOptions(billId).stream().collect(Collectors.toMap(
                option -> option.billLineId() + ":" + option.memberCardId(), Function.identity()));
        Map<Long, BillLine> billLines = bill.lines().stream()
                .collect(Collectors.toMap(BillLine::id, Function.identity()));
        Set<Long> selectedLines = new HashSet<>();
        Set<Long> selectedBalances = new HashSet<>();
        for (SettlementCardSelectionRequest selection : request.cards()) {
            if (!selectedLines.add(selection.billLineId())) {
                throw new IllegalArgumentException("同一账单明细不能重复选择次卡");
            }
            CardSettlementOption option = cardOptionMap.get(selection.billLineId() + ":" + selection.memberCardId());
            if (option == null) throw new IllegalArgumentException("所选次卡不能抵扣当前账单项目");
            if (!selectedBalances.add(option.memberCardBalanceId())) {
                throw new IllegalArgumentException("同一张次卡的同一项目不能在一笔结算中重复使用");
            }
            MemberCardDetail card = cards.findMemberCard(option.memberCardId()).orElseThrow();
            MemberCardBalanceItem balance = card.balances().stream()
                    .filter(item -> item.id() == option.memberCardBalanceId()).findFirst().orElseThrow();
            BillLine line = billLines.get(option.billLineId());
            BigDecimal amount = money(line.receivableAmount());
            assetUsages.add(new SettlementAssetUsage(
                    "CARD", memberId, null, card.card().id(), balance.id(), line.id(), line.itemId(),
                    option.requiredTimes(), amount, balance.version(),
                    card.card().cardTypeName() + "抵扣" + line.itemName()));
            assetTotal = assetTotal.add(amount);
        }

        Set<Long> selectedVouchers = new HashSet<>();
        boolean discountVoucherSelected = false;
        for (Long voucherId : request.voucherCodeIds()) {
            if (voucherId == null || !selectedVouchers.add(voucherId)) {
                throw new IllegalArgumentException("同一张代金券不能重复使用");
            }
            VoucherCodeSummary voucher = benefits.findVoucherCode(voucherId)
                    .orElseThrow(() -> new IllegalArgumentException("代金券不存在"));
            validateVoucher(voucher, memberId, receivable);
            if ("DISCOUNT".equals(voucher.benefitType()) && discountVoucherSelected) {
                throw new IllegalArgumentException("一笔账单最多使用一张折扣券");
            }
            discountVoucherSelected |= "DISCOUNT".equals(voucher.benefitType());
            BigDecimal remaining = receivable.subtract(assetTotal).max(BigDecimal.ZERO).setScale(4);
            BigDecimal amount = voucherAmount(voucher, remaining);
            if (amount.signum() <= 0) throw new IllegalArgumentException("代金券没有可抵扣金额");
            assetUsages.add(new SettlementAssetUsage(
                    "VOUCHER", memberId, voucher.id(), null, null, null, null,
                    BigDecimal.ONE.setScale(4), amount, voucher.version(), voucher.voucherName() + "（" + voucher.code() + "）"));
            assetTotal = assetTotal.add(amount);
        }

        BigDecimal balanceAmount = money(request.balanceAmount());
        if (balanceAmount.signum() > 0) {
            BalanceAccount account = assets.findBalanceAccount(memberId)
                    .orElseThrow(() -> new IllegalArgumentException("会员储值账户不存在"));
            if (account.availableBalance().compareTo(balanceAmount) < 0) {
                throw new IllegalArgumentException("可用储值余额不足");
            }
            if (assetTotal.add(balanceAmount).compareTo(receivable) > 0) {
                throw new IllegalArgumentException("储值抵扣金额超过账单剩余应收");
            }
            assetUsages.add(new SettlementAssetUsage(
                    "BALANCE", memberId, null, null, null, null, null, balanceAmount, balanceAmount,
                    account.version(), "会员储值抵扣"));
            assetTotal = assetTotal.add(balanceAmount);
        }

        if (request.points() > 0) {
            PointAccount account = assets.findPointAccount(memberId)
                    .orElseThrow(() -> new IllegalArgumentException("会员积分账户不存在"));
            if (account.availablePoints() < request.points()) throw new IllegalArgumentException("可用积分不足");
            BigDecimal pointAmount = BigDecimal.valueOf(request.points())
                    .divide(BigDecimal.valueOf(assets.pointsPerYuan()), 4, RoundingMode.HALF_UP);
            if (pointAmount.signum() <= 0) throw new IllegalArgumentException("本次积分不足以抵扣金额");
            if (assetTotal.add(pointAmount).compareTo(receivable) > 0) {
                throw new IllegalArgumentException("积分抵扣金额超过账单剩余应收");
            }
            assetUsages.add(new SettlementAssetUsage(
                    "POINT", memberId, null, null, null, null, null,
                    BigDecimal.valueOf(request.points()), pointAmount,
                    account.version(), request.points() + "积分抵扣"));
            assetTotal = assetTotal.add(pointAmount);
        }

        Map<Long, PaymentMethodOption> methods = repository.paymentMethods(bill.bill().storeId()).stream()
                .collect(Collectors.toMap(PaymentMethodOption::id, Function.identity()));
        List<QuotePayment> payments = new ArrayList<>();
        BigDecimal externalTotal = BigDecimal.ZERO.setScale(4);
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
            externalTotal = externalTotal.add(amount);
            if ("CASH".equals(method.type())) cashTotal = cashTotal.add(amount);
        }
        BigDecimal total = assetTotal.add(externalTotal);
        BigDecimal change = total.subtract(receivable).max(BigDecimal.ZERO).setScale(4, RoundingMode.HALF_UP);
        BigDecimal difference = receivable.subtract(total).max(BigDecimal.ZERO).setScale(4, RoundingMode.HALF_UP);
        if (change.compareTo(cashTotal) > 0) {
            throw new IllegalArgumentException("非现金支付不能产生找零");
        }
        return repository.createQuote(new SettlementQuoteDraft(
                numbers.quoteNo(), billId, bill.bill().version(), receivable, total, assetTotal, externalTotal,
                change, difference, payments, assetUsages,
                LocalDateTime.now().plusMinutes(10), currentUserId(username)));
    }

    @Transactional
    public BillDetail settle(long billId, SettleBillRequest request, String username) {
        String idempotencyKey = trimToNull(request.idempotencyKey());
        Optional<BillDetail> existing = repository.findBySettlementIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            if (existing.get().bill().id() != billId) throw new IllegalArgumentException("结算幂等键已被其他账单使用");
            return existing.get();
        }
        SettlementQuote quote = repository.findQuote(request.quoteNo())
                .orElseThrow(() -> new IllegalArgumentException("结算试算不存在"));
        if (quote.billId() != billId) throw new IllegalArgumentException("结算试算与账单不匹配");
        if (quote.used() || quote.expiresAt().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("结算试算已失效，请重新试算");
        }
        if (quote.differenceAmount().signum() != 0) throw new IllegalArgumentException("支付金额尚未覆盖账单应收");
        BillDetail bill = detail(billId);
        if (!bill.bill().version().equals(quote.billVersion())) {
            throw new DuplicateResourceException("账单已发生变化，请重新试算");
        }
        validateAssetVersions(quote, bill);
        long operatorId = currentUserId(username);
        for (SettlementAssetUsage asset : quote.assets()) {
            switch (asset.assetType()) {
                case "BALANCE" -> assets.consumeBalance(new BalanceSettlementConsumption(
                        billId, asset.memberId(), bill.bill().storeId(), asset.amount(), asset.assetVersion(),
                        asset.displayName(), operatorId));
                case "POINT" -> assets.consumePoints(new PointSettlementConsumption(
                        billId, asset.memberId(), asset.quantity().intValueExact(), asset.amount(),
                        asset.assetVersion(), asset.displayName(), operatorId));
                case "CARD" -> cards.consumeCard(new CardSettlementConsumption(
                        billId, asset.memberId(), asset.memberCardId(), asset.memberCardBalanceId(),
                        asset.billLineId(), asset.serviceId(), asset.quantity(), asset.amount(),
                        bill.lines().stream().filter(line -> line.id() == asset.billLineId())
                                .findFirst().orElseThrow().originalAmount(),
                        asset.assetVersion(), asset.displayName(), operatorId));
                case "VOUCHER" -> benefits.consume(new VoucherSettlementConsumption(
                        billId, asset.memberId(), asset.voucherCodeId(), asset.amount(), asset.assetVersion(),
                        asset.displayName(), operatorId));
                default -> throw new IllegalArgumentException("不支持的会员资产类型");
            }
        }
        BillDetail settled = repository.settle(new SettleBillCommand(
                billId, quote, idempotencyKey, operatorId));
        commissions.recordSettledBill(settled, operatorId);
        return settled;
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

    private List<BillDiscountAllocation> allocateDiscount(
            List<BillLine> lines, BigDecimal original, BigDecimal totalDiscount) {
        List<BillDiscountAllocation> allocations = new ArrayList<>();
        BigDecimal allocated = BigDecimal.ZERO.setScale(4);
        for (int index = 0; index < lines.size(); index++) {
            BillLine line = lines.get(index);
            BigDecimal discount = index == lines.size() - 1
                    ? totalDiscount.subtract(allocated)
                    : totalDiscount.multiply(line.originalAmount())
                            .divide(original, 4, RoundingMode.HALF_UP);
            discount = discount.min(totalDiscount.subtract(allocated)).max(BigDecimal.ZERO);
            if (discount.compareTo(line.originalAmount()) > 0) discount = line.originalAmount();
            discount = money(discount);
            allocated = allocated.add(discount);
            allocations.add(new BillDiscountAllocation(
                    line.id(), line.originalAmount(), discount, line.originalAmount().subtract(discount)));
        }
        if (allocated.compareTo(totalDiscount) != 0) {
            throw new IllegalArgumentException("优惠分摊失败，请调整优惠金额");
        }
        return List.copyOf(allocations);
    }

    private void validateAssetVersions(SettlementQuote quote, BillDetail bill) {
        for (SettlementAssetUsage asset : quote.assets()) {
            if (bill.bill().memberId() == null || bill.bill().memberId() != asset.memberId()) {
                throw new IllegalArgumentException("结算资产与账单会员不匹配");
            }
            switch (asset.assetType()) {
                case "BALANCE" -> {
                    BalanceAccount account = assets.findBalanceAccount(asset.memberId())
                            .orElseThrow(() -> new IllegalArgumentException("会员储值账户不存在"));
                    if (!account.version().equals(asset.assetVersion())) {
                        throw new DuplicateResourceException("储值余额已发生变化，请重新试算");
                    }
                    if (account.availableBalance().compareTo(asset.amount()) < 0) {
                        throw new IllegalArgumentException("可用储值余额不足");
                    }
                }
                case "POINT" -> {
                    PointAccount account = assets.findPointAccount(asset.memberId())
                            .orElseThrow(() -> new IllegalArgumentException("会员积分账户不存在"));
                    if (!account.version().equals(asset.assetVersion())) {
                        throw new DuplicateResourceException("积分余额已发生变化，请重新试算");
                    }
                    if (account.availablePoints() < asset.quantity().intValueExact()) {
                        throw new IllegalArgumentException("可用积分不足");
                    }
                }
                case "CARD" -> {
                    MemberCardDetail card = cards.findMemberCard(asset.memberCardId())
                            .orElseThrow(() -> new IllegalArgumentException("会员次卡不存在"));
                    if (card.card().memberId() != asset.memberId() || !"ACTIVE".equals(card.card().status())
                            || card.card().expiresAt().isBefore(LocalDateTime.now())) {
                        throw new IllegalArgumentException("会员次卡已失效或不属于当前会员");
                    }
                    MemberCardBalanceItem balance = card.balances().stream()
                            .filter(item -> item.id() == asset.memberCardBalanceId()
                                    && item.serviceId() == asset.serviceId())
                            .findFirst().orElseThrow(() -> new IllegalArgumentException("次卡不支持当前项目"));
                    if (!balance.version().equals(asset.assetVersion())) {
                        throw new DuplicateResourceException("次卡次数已发生变化，请重新试算");
                    }
                    if (balance.remainingTimes().compareTo(asset.quantity()) < 0) {
                        throw new IllegalArgumentException("次卡剩余次数不足");
                    }
                }
                case "VOUCHER" -> {
                    VoucherCodeSummary voucher = benefits.findVoucherCode(asset.voucherCodeId())
                            .orElseThrow(() -> new IllegalArgumentException("代金券不存在"));
                    validateVoucher(voucher, asset.memberId(), bill.bill().receivableAmount());
                    if (!voucher.version().equals(asset.assetVersion())) {
                        throw new DuplicateResourceException("代金券状态已变化，请重新试算");
                    }
                }
                default -> throw new IllegalArgumentException("不支持的会员资产类型");
            }
        }
    }

    private void requireMutable(BillSummary bill) {
        if (!MUTABLE_BILL_STATUSES.contains(bill.status())) {
            throw new IllegalArgumentException("当前账单状态不允许修改或结算");
        }
    }

    private void validateVoucher(VoucherCodeSummary voucher, Long memberId, BigDecimal receivable) {
        LocalDateTime now = LocalDateTime.now();
        if (memberId == null || !java.util.Objects.equals(voucher.memberId(), memberId)) {
            throw new IllegalArgumentException("代金券不属于当前账单会员");
        }
        if (!"BOUND".equals(voucher.status())) throw new IllegalArgumentException("代金券当前不可使用");
        if (voucher.validFrom().isAfter(now) || voucher.validUntil().isBefore(now)) {
            throw new IllegalArgumentException("代金券不在有效期内");
        }
        if (receivable.compareTo(voucher.minSpend()) < 0) throw new IllegalArgumentException("账单金额未达到代金券门槛");
    }

    private BigDecimal voucherAmount(VoucherCodeSummary voucher, BigDecimal remaining) {
        if (remaining.signum() <= 0) return BigDecimal.ZERO.setScale(4);
        if ("FIXED_AMOUNT".equals(voucher.benefitType())) return money(voucher.faceAmount().min(remaining));
        return money(remaining.multiply(BigDecimal.ONE.subtract(voucher.discountRate()))).min(remaining);
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
