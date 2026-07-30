package com.yuezhijian.server.inventory;

import com.yuezhijian.server.audit.AuditService;
import com.yuezhijian.server.common.DuplicateResourceException;
import com.yuezhijian.server.common.PageResult;
import com.yuezhijian.server.common.ResourceNotFoundException;
import com.yuezhijian.server.iam.AccessCatalogService;
import com.yuezhijian.server.iam.StoreDataScope;
import com.yuezhijian.server.masterdata.CategoryOption;
import com.yuezhijian.server.masterdata.MasterDataRepository;
import com.yuezhijian.server.masterdata.UnitOption;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InventoryService {
    private static final Set<String> GIFT_STATUSES = Set.of("ACTIVE", "DISABLED");
    private static final Set<String> TRANSFER_STATUSES = Set.of("DRAFT", "CONFIRMED", "VOIDED", "REVERSED");
    private static final Set<String> COUNT_STATUSES = Set.of("DRAFT", "READY_CONFIRM", "CONFIRMED", "VOIDED");

    private final InventoryRepository repository;
    private final MasterDataRepository masterData;
    private final AccessCatalogService accessCatalog;
    private final StoreDataScope storeDataScope;
    private final InventoryNumberGenerator numbers;
    private final AuditService audit;

    public InventoryService(
            InventoryRepository repository,
            MasterDataRepository masterData,
            AccessCatalogService accessCatalog,
            StoreDataScope storeDataScope,
            InventoryNumberGenerator numbers,
            AuditService audit) {
        this.repository = repository;
        this.masterData = masterData;
        this.accessCatalog = accessCatalog;
        this.storeDataScope = storeDataScope;
        this.numbers = numbers;
        this.audit = audit;
    }

    public PageResult<Gift> gifts(String keyword, String status, int page, int size) {
        Page safe = page(page, size);
        return repository.gifts(optional(keyword, 200, "查询关键字"),
                optionalEnum(status, GIFT_STATUSES, "礼品状态无效"), safe.page(), safe.size());
    }

    public Gift gift(long id) {
        return repository.findGift(id).orElseThrow(() -> new ResourceNotFoundException("礼品不存在"));
    }

    @Transactional
    public Gift createGift(CreateGiftRequest request, String username) {
        CategoryOption category = requireGiftCategory(request.categoryId());
        UnitOption unit = requireUnit(request.unitId());
        String code = request.code().trim().toUpperCase(Locale.ROOT);
        if (!code.matches("[A-Z0-9][A-Z0-9_-]{1,63}")) throw new IllegalArgumentException("礼品编号格式无效");
        if (repository.findGiftByCode(code).isPresent()) throw new DuplicateResourceException("礼品编号已存在");
        BigDecimal cost = quantity(request.costPrice(), 4, true, "礼品成本");
        BigDecimal threshold = quantity(
                request.lowStockThreshold(), unit.decimalPlaces(), true, "低库存阈值");
        long operatorId = operatorId(username);
        Gift created = repository.createGift(new NewGift(
                code, request.name().trim(), category.id(), unit.id(), request.pointPrice(), cost,
                threshold, optional(request.description(), 1000, "礼品说明"), operatorId));
        audit.record("INVENTORY", "CREATE", "GIFT", created.id(), null,
                null, giftSnapshot(created), operatorId);
        return created;
    }

    @Transactional
    public Gift updateGift(long id, UpdateGiftRequest request, String username) {
        Gift before = gift(id);
        CategoryOption category = requireGiftCategory(request.categoryId());
        UnitOption unit = requireUnit(request.unitId());
        String status = requiredEnum(request.status(), GIFT_STATUSES, "礼品状态无效");
        long operatorId = operatorId(username);
        Gift updated = repository.updateGift(new GiftUpdate(
                id, request.name().trim(), category.id(), unit.id(), request.pointPrice(),
                quantity(request.costPrice(), 4, true, "礼品成本"),
                quantity(request.lowStockThreshold(), unit.decimalPlaces(), true, "低库存阈值"),
                optional(request.description(), 1000, "礼品说明"), status, request.version(), operatorId));
        audit.record("INVENTORY", "UPDATE", "GIFT", id, null,
                giftSnapshot(before), giftSnapshot(updated), operatorId);
        return updated;
    }

    public PageResult<StockItem> stocks(
            Long storeId, String keyword, Boolean lowStock, int page, int size) {
        Page safe = page(page, size);
        return repository.stocks(storeDataScope.resolveRequired(storeId),
                optional(keyword, 200, "查询关键字"), lowStock, safe.page(), safe.size());
    }

    public PageResult<StockLedgerItem> stockLedgers(
            long storeId, long giftId, int page, int size) {
        storeDataScope.require(storeId);
        gift(giftId);
        Page safe = page(page, size);
        return repository.stockLedgers(storeId, giftId, safe.page(), safe.size());
    }

    public PageResult<TransferSummary> transfers(
            Long storeId, String keyword, String status, int page, int size) {
        Page safe = page(page, size);
        return repository.transfers(
                storeDataScope.constrainNullable(storeId), optional(keyword, 200, "查询关键字"),
                optionalEnum(status, TRANSFER_STATUSES, "调拨状态无效"), safe.page(), safe.size());
    }

    public TransferDetail transfer(long id) {
        TransferDetail detail = repository.findTransfer(id)
                .orElseThrow(() -> new ResourceNotFoundException("调拨单不存在"));
        storeDataScope.requireAny(List.of(detail.sourceStoreId(), detail.targetStoreId()));
        return detail;
    }

    @Transactional
    public TransferDetail createTransfer(CreateTransferRequest request, String username) {
        if (request.sourceStoreId() == request.targetStoreId()) throw new IllegalArgumentException("调出和调入门店不能相同");
        storeDataScope.require(request.sourceStoreId());
        storeDataScope.require(request.targetStoreId());
        if (request.transferDate().isAfter(LocalDate.now())) throw new IllegalArgumentException("调拨日期不能晚于今天");
        List<TransferLineRequest> lines = normalizeTransferLines(request.lines());
        long operatorId = operatorId(username);
        TransferDetail created = repository.createTransfer(new NewTransfer(
                numbers.transferNo(), request.sourceStoreId(), request.targetStoreId(), request.transferDate(),
                optional(request.remarks(), 500, "调拨备注"), lines, request.idempotencyKey().trim(), operatorId));
        audit.record("INVENTORY", "CREATE", "INVENTORY_TRANSFER", created.id(), created.sourceStoreId(),
                null, transferSnapshot(created), operatorId);
        return created;
    }

    @Transactional
    public TransferDetail confirmTransfer(long id, ConfirmInventoryRequest request, String username) {
        TransferDetail before = transfer(id);
        requireAllTransferStores(before);
        if ("CONFIRMED".equals(before.status())) return before;
        long operatorId = operatorId(username);
        TransferDetail updated = repository.confirmTransfer(
                id, request.version(), optional(request.reason(), 500, "确认说明"), operatorId);
        audit.record("INVENTORY", "CONFIRM", "INVENTORY_TRANSFER", id, updated.sourceStoreId(),
                transferSnapshot(before), transferSnapshot(updated), operatorId);
        return updated;
    }

    @Transactional
    public TransferDetail voidTransfer(long id, InventoryActionRequest request, String username) {
        TransferDetail before = transfer(id);
        requireAllTransferStores(before);
        if ("VOIDED".equals(before.status())) return before;
        long operatorId = operatorId(username);
        TransferDetail updated = repository.voidTransfer(
                id, request.version(), request.reason().trim(), operatorId);
        audit.record("INVENTORY", "VOID", "INVENTORY_TRANSFER", id, updated.sourceStoreId(),
                transferSnapshot(before), transferSnapshot(updated), operatorId);
        return updated;
    }

    @Transactional
    public TransferDetail reverseTransfer(long id, InventoryActionRequest request, String username) {
        TransferDetail before = transfer(id);
        requireAllTransferStores(before);
        if ("REVERSED".equals(before.status())) return before;
        long operatorId = operatorId(username);
        TransferDetail updated = repository.reverseTransfer(
                id, request.version(), request.reason().trim(), operatorId);
        audit.record("INVENTORY", "REVERSE", "INVENTORY_TRANSFER", id, updated.sourceStoreId(),
                transferSnapshot(before), transferSnapshot(updated), operatorId);
        return updated;
    }

    public PageResult<CountSummary> counts(
            Long storeId, String keyword, String status, int page, int size) {
        Page safe = page(page, size);
        return repository.counts(
                storeDataScope.constrainNullable(storeId), optional(keyword, 200, "查询关键字"),
                optionalEnum(status, COUNT_STATUSES, "盘点状态无效"), safe.page(), safe.size());
    }

    public CountDetail count(long id) {
        CountDetail detail = repository.findCount(id)
                .orElseThrow(() -> new ResourceNotFoundException("盘点单不存在"));
        storeDataScope.require(detail.storeId());
        return detail;
    }

    @Transactional
    public CountDetail createCount(CreateCountRequest request, String username) {
        storeDataScope.require(request.storeId());
        if (request.countDate().isAfter(LocalDate.now())) throw new IllegalArgumentException("盘点日期不能晚于今天");
        String name = request.name().trim();
        if (name.isEmpty()) throw new IllegalArgumentException("盘点单名称不能为空");
        List<Long> giftIds = new ArrayList<>(new LinkedHashSet<>(request.giftIds()));
        if (giftIds.size() != request.giftIds().size()) throw new IllegalArgumentException("盘点礼品不能重复");
        // 已停用但仍有结存的礼品必须允许盘点清账，停用只禁止新建礼品业务。
        giftIds.forEach(this::gift);
        giftIds.sort(Long::compareTo);
        long operatorId = operatorId(username);
        CountDetail created = repository.createCount(new NewCount(
                numbers.countNo(), name, request.storeId(), request.countDate(), giftIds,
                optional(request.remarks(), 500, "盘点备注"), request.idempotencyKey().trim(), operatorId));
        audit.record("INVENTORY", "CREATE", "INVENTORY_COUNT", created.id(), created.storeId(),
                null, countSnapshot(created), operatorId);
        return created;
    }

    @Transactional
    public CountDetail saveCountLines(
            long id, SaveCountLinesRequest request, String username) {
        CountDetail before = count(id);
        Map<Long, CountLine> existing = new LinkedHashMap<>();
        before.lines().forEach(line -> existing.put(line.id(), line));
        Set<Long> submitted = new LinkedHashSet<>();
        List<CountLineInput> normalized = request.lines().stream().map(input -> {
            if (!submitted.add(input.lineId())) throw new IllegalArgumentException("盘点明细不能重复");
            CountLine line = existing.get(input.lineId());
            if (line == null) throw new IllegalArgumentException("盘点明细不属于当前单据");
            return new CountLineInput(input.lineId(),
                    quantity(input.actualQuantity(), line.unitDecimalPlaces(), true, "实盘数量"));
        }).toList();
        if (!submitted.equals(existing.keySet())) throw new IllegalArgumentException("必须提交盘点单全部明细");
        long operatorId = operatorId(username);
        CountDetail updated = repository.saveCountLines(id, request.version(), normalized, operatorId);
        audit.record("INVENTORY", "SAVE_LINES", "INVENTORY_COUNT", id, updated.storeId(),
                countSnapshot(before), countSnapshot(updated), operatorId);
        return updated;
    }

    @Transactional
    public CountDetail confirmCount(long id, ConfirmInventoryRequest request, String username) {
        CountDetail before = count(id);
        if ("CONFIRMED".equals(before.status())) return before;
        long operatorId = operatorId(username);
        CountDetail updated = repository.confirmCount(
                id, request.version(), optional(request.reason(), 500, "确认说明"), operatorId);
        audit.record("INVENTORY", "CONFIRM", "INVENTORY_COUNT", id, updated.storeId(),
                countSnapshot(before), countSnapshot(updated), operatorId);
        return updated;
    }

    @Transactional
    public CountDetail voidCount(long id, InventoryActionRequest request, String username) {
        CountDetail before = count(id);
        if ("VOIDED".equals(before.status())) return before;
        long operatorId = operatorId(username);
        CountDetail updated = repository.voidCount(
                id, request.version(), request.reason().trim(), operatorId);
        audit.record("INVENTORY", "VOID", "INVENTORY_COUNT", id, updated.storeId(),
                countSnapshot(before), countSnapshot(updated), operatorId);
        return updated;
    }

    private List<TransferLineRequest> normalizeTransferLines(List<TransferLineRequest> lines) {
        Set<Long> giftIds = new LinkedHashSet<>();
        List<TransferLineRequest> normalized = new ArrayList<>();
        for (TransferLineRequest line : lines) {
            if (!giftIds.add(line.giftId())) throw new IllegalArgumentException("同一礼品只能填写一行");
            // 已确认历史礼品可能已停用，仍需允许在门店之间调拨并清理结存。
            Gift gift = gift(line.giftId());
            normalized.add(new TransferLineRequest(
                    line.giftId(), quantity(line.quantity(), gift.unitDecimalPlaces(), false, "调拨数量"),
                    optional(line.note(), 200, "调拨明细备注")));
        }
        normalized.sort(java.util.Comparator.comparingLong(TransferLineRequest::giftId));
        return normalized;
    }

    private void requireAllTransferStores(TransferDetail detail) {
        storeDataScope.require(detail.sourceStoreId());
        storeDataScope.require(detail.targetStoreId());
    }

    private Gift requireActiveGift(long id) {
        Gift gift = gift(id);
        if (!"ACTIVE".equals(gift.status())) throw new IllegalArgumentException("礼品已停用：" + gift.name());
        return gift;
    }

    private CategoryOption requireGiftCategory(long id) {
        return masterData.categories("GIFT").stream()
                .filter(item -> item.id() == id && "ACTIVE".equals(item.status())).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("礼品分类不存在或已停用"));
    }

    private UnitOption requireUnit(long id) {
        return masterData.units().stream().filter(item -> item.id() == id && "ACTIVE".equals(item.status()))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("礼品单位不存在或已停用"));
    }

    private BigDecimal quantity(BigDecimal value, int decimals, boolean allowZero, String label) {
        BigDecimal normalized = value == null ? null : value.stripTrailingZeros();
        int effectiveScale = normalized == null ? 0 : Math.max(normalized.scale(), 0);
        int integerDigits = normalized == null ? 0
                : Math.max(normalized.precision() - normalized.scale(), 0);
        if (normalized == null || effectiveScale > decimals || integerDigits > 15
                || (allowZero ? normalized.signum() < 0 : normalized.signum() <= 0)) {
            String rule = allowZero ? "非负数" : "正数";
            throw new IllegalArgumentException(label + "必须是最多15位整数、" + decimals + "位小数的" + rule);
        }
        return normalized.setScale(4);
    }

    private long operatorId(String username) { return accessCatalog.userIdentity(username).id(); }

    private static Page page(int page, int size) {
        if (page < 1) throw new IllegalArgumentException("页码必须从1开始");
        if (size < 1 || size > 100) throw new IllegalArgumentException("每页数量必须在1到100之间");
        return new Page(page, size);
    }

    private static String optional(String value, int max, String label) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim();
        if (normalized.length() > max) throw new IllegalArgumentException(label + "不能超过" + max + "个字符");
        return normalized;
    }

    private static String optionalEnum(String value, Set<String> supported, String message) {
        return value == null || value.isBlank() ? null : requiredEnum(value, supported, message);
    }

    private static String requiredEnum(String value, Set<String> supported, String message) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(message);
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!supported.contains(normalized)) throw new IllegalArgumentException(message);
        return normalized;
    }

    private static Map<String, Object> giftSnapshot(Gift gift) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("code", gift.code());
        value.put("name", gift.name());
        value.put("categoryName", gift.categoryName());
        value.put("unitName", gift.unitName());
        value.put("pointPrice", gift.pointPrice());
        value.put("costPrice", gift.costPrice());
        value.put("lowStockThreshold", gift.lowStockThreshold());
        value.put("status", gift.status());
        return value;
    }

    private static Map<String, Object> transferSnapshot(TransferDetail detail) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("transferNo", detail.transferNo());
        value.put("sourceStoreName", detail.sourceStoreName());
        value.put("targetStoreName", detail.targetStoreName());
        value.put("transferDate", detail.transferDate());
        value.put("status", detail.status());
        value.put("actionReason", detail.actionReason());
        value.put("lines", detail.lines().stream().map(line -> Map.of(
                "giftCode", line.giftCode(), "quantity", line.quantity())).toList());
        return value;
    }

    private static Map<String, Object> countSnapshot(CountDetail detail) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("countNo", detail.countNo());
        value.put("name", detail.name());
        value.put("storeName", detail.storeName());
        value.put("countDate", detail.countDate());
        value.put("status", detail.status());
        value.put("actionReason", detail.actionReason());
        value.put("lines", detail.lines().stream().map(line -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("giftCode", line.giftCode());
            item.put("bookQuantity", line.bookQuantity());
            item.put("actualQuantity", line.actualQuantity());
            item.put("differenceQuantity", line.differenceQuantity());
            return item;
        }).toList());
        return value;
    }

    private record Page(int page, int size) {}
}
