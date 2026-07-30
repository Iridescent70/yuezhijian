package com.yuezhijian.server.inventory;

import com.yuezhijian.server.common.DuplicateResourceException;
import com.yuezhijian.server.common.PageResult;
import com.yuezhijian.server.common.ResourceNotFoundException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("memory")
public class MemoryInventoryRepository implements InventoryRepository {
    private final InventoryNumberGenerator numbers;
    private final AtomicLong giftIds = new AtomicLong(501);
    private final AtomicLong stockIds = new AtomicLong(1);
    private final AtomicLong ledgerIds = new AtomicLong();
    private final AtomicLong transferIds = new AtomicLong();
    private final AtomicLong transferLineIds = new AtomicLong();
    private final AtomicLong countIds = new AtomicLong();
    private final AtomicLong countLineIds = new AtomicLong();
    private final Map<Long, Gift> gifts = new LinkedHashMap<>();
    private final Map<StockKey, StockEntry> stocks = new HashMap<>();
    private final List<StockLedgerItem> ledgers = new ArrayList<>();
    private final Map<Long, TransferEntry> transfers = new LinkedHashMap<>();
    private final Map<Long, CountEntry> counts = new LinkedHashMap<>();

    public MemoryInventoryRepository(InventoryNumberGenerator numbers) {
        this.numbers = numbers;
        gifts.put(501L, new Gift(
                501L, "GFT001", "指缘护理礼包", 3L, "积分礼品", 2L, "件", 0, 500,
                new BigDecimal("35.0000"), new BigDecimal("5.0000"), "门店积分兑换礼品", "ACTIVE", "1"));
        stocks.put(new StockKey(2L, 501L), new StockEntry(
                stockIds.getAndIncrement(), 2L, 501L, new BigDecimal("20.0000"), 1));
    }

    @Override
    public synchronized PageResult<Gift> gifts(String keyword, String status, int page, int size) {
        String normalized = lower(keyword);
        List<Gift> matched = gifts.values().stream()
                .filter(gift -> status == null || status.equals(gift.status()))
                .filter(gift -> normalized == null || lower(gift.code()).contains(normalized)
                        || lower(gift.name()).contains(normalized))
                .sorted(Comparator.comparingLong(Gift::id).reversed()).toList();
        return page(matched, page, size);
    }

    @Override
    public synchronized Optional<Gift> findGift(long id) {
        return Optional.ofNullable(gifts.get(id));
    }

    @Override
    public synchronized Optional<Gift> findGiftByCode(String code) {
        return gifts.values().stream().filter(gift -> gift.code().equalsIgnoreCase(code)).findFirst();
    }

    @Override
    public synchronized Gift createGift(NewGift draft) {
        if (findGiftByCode(draft.code()).isPresent()) throw new DuplicateResourceException("礼品编号已存在");
        long id = giftIds.incrementAndGet();
        Gift gift = new Gift(
                id, draft.code(), draft.name(), draft.categoryId(), categoryName(draft.categoryId()),
                draft.unitId(), unitName(draft.unitId()), unitDecimals(draft.unitId()), draft.pointPrice(),
                draft.costPrice(), draft.lowStockThreshold(), draft.description(), "ACTIVE", "1");
        gifts.put(id, gift);
        return gift;
    }

    @Override
    public synchronized Gift updateGift(GiftUpdate update) {
        Gift current = requireGift(update.id());
        requireVersion(current.version(), update.version(), "礼品资料");
        Gift saved = new Gift(
                current.id(), current.code(), update.name(), update.categoryId(), categoryName(update.categoryId()),
                update.unitId(), unitName(update.unitId()), unitDecimals(update.unitId()), update.pointPrice(),
                update.costPrice(), update.lowStockThreshold(), update.description(), update.status(),
                next(current.version()));
        gifts.put(saved.id(), saved);
        return saved;
    }

    @Override
    public synchronized PageResult<StockItem> stocks(
            long storeId, String keyword, Boolean lowStock, int page, int size) {
        String normalized = lower(keyword);
        List<StockItem> matched = gifts.values().stream()
                .filter(gift -> normalized == null || lower(gift.code()).contains(normalized)
                        || lower(gift.name()).contains(normalized))
                .map(gift -> stockItem(storeId, gift))
                .filter(item -> lowStock == null || lowStock == item.lowStock())
                .sorted(Comparator.comparing(StockItem::giftCode)).toList();
        return page(matched, page, size);
    }

    @Override
    public synchronized PageResult<StockLedgerItem> stockLedgers(
            long storeId, long giftId, int page, int size) {
        List<StockLedgerItem> matched = ledgers.stream()
                .filter(item -> item.storeId() == storeId && item.giftId() == giftId)
                .sorted(Comparator.comparing(StockLedgerItem::occurredAt).reversed()
                        .thenComparing(StockLedgerItem::id, Comparator.reverseOrder()))
                .toList();
        return page(matched, page, size);
    }

    @Override
    public synchronized PageResult<TransferSummary> transfers(
            Long storeId, String keyword, String status, int page, int size) {
        String normalized = lower(keyword);
        List<TransferSummary> matched = transfers.values().stream()
                .filter(item -> storeId == null || item.sourceStoreId == storeId || item.targetStoreId == storeId)
                .filter(item -> status == null || status.equals(item.status))
                .filter(item -> normalized == null || lower(item.transferNo).contains(normalized))
                .sorted(Comparator.comparing((TransferEntry item) -> item.createdAt).reversed())
                .map(TransferEntry::summary).toList();
        return page(matched, page, size);
    }

    @Override
    public synchronized Optional<TransferDetail> findTransfer(long id) {
        return Optional.ofNullable(transfers.get(id)).map(TransferEntry::detail);
    }

    @Override
    public synchronized TransferDetail createTransfer(NewTransfer draft) {
        TransferEntry existing = transfers.values().stream()
                .filter(item -> item.idempotencyKey.equals(draft.idempotencyKey())).findFirst().orElse(null);
        if (existing != null) {
            if (!existing.sameRequest(draft)) throw new DuplicateResourceException("幂等键已用于其他调拨单");
            return existing.detail();
        }
        long id = transferIds.incrementAndGet();
        List<MutableTransferLine> lines = draft.lines().stream().map(line -> new MutableTransferLine(
                transferLineIds.incrementAndGet(), requireGift(line.giftId()), line.quantity(), line.note()))
                .toList();
        TransferEntry entry = new TransferEntry(id, draft, lines);
        transfers.put(id, entry);
        return entry.detail();
    }

    @Override
    public synchronized TransferDetail confirmTransfer(
            long id, String version, String reason, long operatorId) {
        TransferEntry entry = requireTransfer(id);
        if ("CONFIRMED".equals(entry.status)) return entry.detail();
        requireStatus(entry.status, "DRAFT", "只有草稿调拨单可以确认");
        requireVersion(entry.version(), version, "调拨单");
        for (MutableTransferLine line : entry.sortedLines()) {
            StockEntry source = stock(entry.sourceStoreId, line.gift.id());
            if (source.quantity.compareTo(line.quantity) < 0) {
                throw new IllegalArgumentException(line.gift.name() + "库存不足");
            }
        }
        for (MutableTransferLine line : entry.sortedLines()) {
            StockEntry source = stock(entry.sourceStoreId, line.gift.id());
            StockEntry target = stock(entry.targetStoreId, line.gift.id());
            line.sourceLedgerId = mutate(source, line.gift, line.quantity.negate(), "TRANSFER_OUT",
                    "TRANSFER", entry.id, line.id, null, entry.transferNo, operatorId);
            line.targetLedgerId = mutate(target, line.gift, line.quantity, "TRANSFER_IN",
                    "TRANSFER", entry.id, line.id, null, entry.transferNo, operatorId);
        }
        entry.status = "CONFIRMED";
        entry.confirmedAt = LocalDateTime.now();
        entry.actionReason = reason;
        entry.version++;
        return entry.detail();
    }

    @Override
    public synchronized TransferDetail voidTransfer(
            long id, String version, String reason, long operatorId) {
        TransferEntry entry = requireTransfer(id);
        if ("VOIDED".equals(entry.status)) return entry.detail();
        requireStatus(entry.status, "DRAFT", "只有草稿调拨单可以作废");
        requireVersion(entry.version(), version, "调拨单");
        entry.status = "VOIDED";
        entry.voidedAt = LocalDateTime.now();
        entry.actionReason = reason;
        entry.version++;
        return entry.detail();
    }

    @Override
    public synchronized TransferDetail reverseTransfer(
            long id, String version, String reason, long operatorId) {
        TransferEntry entry = requireTransfer(id);
        if ("REVERSED".equals(entry.status)) return entry.detail();
        requireStatus(entry.status, "CONFIRMED", "只有已确认调拨单可以冲销");
        requireVersion(entry.version(), version, "调拨单");
        for (MutableTransferLine line : entry.sortedLines()) {
            StockEntry target = stock(entry.targetStoreId, line.gift.id());
            if (target.quantity.compareTo(line.quantity) < 0) {
                throw new IllegalArgumentException(line.gift.name() + "调入门店库存不足，不能冲销");
            }
        }
        for (MutableTransferLine line : entry.sortedLines()) {
            StockEntry source = stock(entry.sourceStoreId, line.gift.id());
            StockEntry target = stock(entry.targetStoreId, line.gift.id());
            mutate(target, line.gift, line.quantity.negate(), "TRANSFER_REVERSAL_OUT", "TRANSFER_REVERSAL",
                    entry.id, line.id, line.targetLedgerId, reason, operatorId);
            mutate(source, line.gift, line.quantity, "TRANSFER_REVERSAL_IN", "TRANSFER_REVERSAL",
                    entry.id, line.id, line.sourceLedgerId, reason, operatorId);
        }
        entry.status = "REVERSED";
        entry.reversedAt = LocalDateTime.now();
        entry.actionReason = reason;
        entry.version++;
        return entry.detail();
    }

    @Override
    public synchronized PageResult<CountSummary> counts(
            Long storeId, String keyword, String status, int page, int size) {
        String normalized = lower(keyword);
        List<CountSummary> matched = counts.values().stream()
                .filter(item -> storeId == null || item.storeId == storeId)
                .filter(item -> status == null || status.equals(item.status))
                .filter(item -> normalized == null || lower(item.countNo).contains(normalized)
                        || lower(item.name).contains(normalized))
                .sorted(Comparator.comparing((CountEntry item) -> item.createdAt).reversed())
                .map(CountEntry::summary).toList();
        return page(matched, page, size);
    }

    @Override
    public synchronized Optional<CountDetail> findCount(long id) {
        return Optional.ofNullable(counts.get(id)).map(CountEntry::detail);
    }

    @Override
    public synchronized CountDetail createCount(NewCount draft) {
        CountEntry existing = counts.values().stream()
                .filter(item -> item.idempotencyKey.equals(draft.idempotencyKey())).findFirst().orElse(null);
        if (existing != null) {
            if (!existing.sameRequest(draft)) throw new DuplicateResourceException("幂等键已用于其他盘点单");
            return existing.detail();
        }
        long id = countIds.incrementAndGet();
        List<MutableCountLine> lines = draft.giftIds().stream().map(giftId -> {
            Gift gift = requireGift(giftId);
            return new MutableCountLine(countLineIds.incrementAndGet(), gift,
                    stock(draft.storeId(), giftId).quantity);
        }).toList();
        CountEntry entry = new CountEntry(id, draft, lines);
        counts.put(id, entry);
        return entry.detail();
    }

    @Override
    public synchronized CountDetail saveCountLines(
            long id, String version, List<CountLineInput> inputs, long operatorId) {
        CountEntry entry = requireCount(id);
        requireOneOf(entry.status, List.of("DRAFT", "READY_CONFIRM"), "当前盘点单不能录入实盘数");
        requireVersion(entry.version(), version, "盘点单");
        Map<Long, CountLineInput> byId = new LinkedHashMap<>();
        inputs.forEach(input -> {
            if (byId.put(input.lineId(), input) != null) throw new IllegalArgumentException("盘点明细不能重复");
        });
        if (!byId.keySet().equals(entry.lineIds())) throw new IllegalArgumentException("必须提交盘点单全部明细");
        entry.lines.forEach(line -> line.actualQuantity = byId.get(line.id).actualQuantity());
        entry.status = "READY_CONFIRM";
        entry.version++;
        return entry.detail();
    }

    @Override
    public synchronized CountDetail confirmCount(
            long id, String version, String reason, long operatorId) {
        CountEntry entry = requireCount(id);
        if ("CONFIRMED".equals(entry.status)) return entry.detail();
        requireStatus(entry.status, "READY_CONFIRM", "盘点明细未全部录入，不能确认");
        requireVersion(entry.version(), version, "盘点单");
        for (MutableCountLine line : entry.sortedLines()) {
            StockEntry stock = stock(entry.storeId, line.gift.id());
            if (stock.quantity.compareTo(line.bookQuantity) != 0) {
                throw new DuplicateResourceException(
                        line.gift.name() + "账面库存已变化，请作废本单后重新盘点");
            }
        }
        for (MutableCountLine line : entry.sortedLines()) {
            BigDecimal difference = line.actualQuantity.subtract(line.bookQuantity);
            if (difference.signum() == 0) continue;
            line.stockLedgerId = mutate(stock(entry.storeId, line.gift.id()), line.gift, difference,
                    difference.signum() > 0 ? "COUNT_GAIN" : "COUNT_LOSS", "COUNT", entry.id, line.id,
                    null, reason, operatorId);
        }
        entry.status = "CONFIRMED";
        entry.confirmedAt = LocalDateTime.now();
        entry.actionReason = reason;
        entry.version++;
        return entry.detail();
    }

    @Override
    public synchronized CountDetail voidCount(
            long id, String version, String reason, long operatorId) {
        CountEntry entry = requireCount(id);
        if ("VOIDED".equals(entry.status)) return entry.detail();
        requireOneOf(entry.status, List.of("DRAFT", "READY_CONFIRM"), "当前盘点单不能作废");
        requireVersion(entry.version(), version, "盘点单");
        entry.status = "VOIDED";
        entry.voidedAt = LocalDateTime.now();
        entry.actionReason = reason;
        entry.version++;
        return entry.detail();
    }

    private long mutate(
            StockEntry stock, Gift gift, BigDecimal change, String type, String sourceType,
            long sourceId, Long sourceLineId, Long reversedLedgerId, String note, long operatorId) {
        BigDecimal before = stock.quantity;
        BigDecimal after = before.add(change);
        if (after.signum() < 0) throw new IllegalArgumentException(gift.name() + "库存不足");
        stock.quantity = after;
        stock.version++;
        long id = ledgerIds.incrementAndGet();
        ledgers.add(new StockLedgerItem(
                id, numbers.ledgerNo(), stock.storeId, storeName(stock.storeId), gift.id(), gift.code(), gift.name(),
                type, before, change, after, sourceType, sourceId, sourceLineId, LocalDateTime.now(),
                reversedLedgerId, note, operatorId == 1 ? "本地管理员" : "用户" + operatorId));
        return id;
    }

    private StockItem stockItem(long storeId, Gift gift) {
        StockEntry stock = stocks.get(new StockKey(storeId, gift.id()));
        BigDecimal quantity = stock == null ? BigDecimal.ZERO.setScale(4) : stock.quantity;
        return new StockItem(
                storeId, storeName(storeId), gift.id(), gift.code(), gift.name(), gift.unitName(),
                gift.unitDecimalPlaces(), quantity, gift.lowStockThreshold(),
                quantity.compareTo(gift.lowStockThreshold()) <= 0,
                gift.status(),
                stock == null ? "0" : String.valueOf(stock.version));
    }

    private StockEntry stock(long storeId, long giftId) {
        return stocks.computeIfAbsent(new StockKey(storeId, giftId),
                key -> new StockEntry(stockIds.getAndIncrement(), storeId, giftId, BigDecimal.ZERO.setScale(4), 1));
    }

    private Gift requireGift(long id) {
        Gift gift = gifts.get(id);
        if (gift == null) throw new ResourceNotFoundException("礼品不存在");
        return gift;
    }

    private TransferEntry requireTransfer(long id) {
        TransferEntry entry = transfers.get(id);
        if (entry == null) throw new ResourceNotFoundException("调拨单不存在");
        return entry;
    }

    private CountEntry requireCount(long id) {
        CountEntry entry = counts.get(id);
        if (entry == null) throw new ResourceNotFoundException("盘点单不存在");
        return entry;
    }

    private static void requireVersion(String current, String requested, String label) {
        if (!current.equals(requested)) throw new DuplicateResourceException(label + "已被他人修改，请刷新后重试");
    }

    private static void requireStatus(String current, String expected, String message) {
        if (!expected.equals(current)) throw new IllegalArgumentException(message);
    }

    private static void requireOneOf(String current, List<String> expected, String message) {
        if (!expected.contains(current)) throw new IllegalArgumentException(message);
    }

    private static String next(String version) { return String.valueOf(Long.parseLong(version) + 1); }
    private static String lower(String value) {
        return value == null ? null : value.toLowerCase(Locale.ROOT);
    }
    private static String categoryName(long id) { return id == 3 ? "积分礼品" : "礼品分类" + id; }
    private static String unitName(long id) { return switch ((int) id) { case 1 -> "次"; case 2 -> "件"; case 3 -> "瓶"; default -> "单位" + id; }; }
    private static int unitDecimals(long id) { return id == 3 ? 2 : 0; }
    private static String storeName(long id) { return id == 1 ? "悦指间总部" : id == 2 ? "悦指间示范店" : "门店" + id; }

    private static <T> PageResult<T> page(List<T> items, int page, int size) {
        int from = Math.min((page - 1) * size, items.size());
        int to = Math.min(from + size, items.size());
        return new PageResult<>(items.subList(from, to), page, size, items.size());
    }

    private record StockKey(long storeId, long giftId) {}

    private static final class StockEntry {
        private final long id;
        private final long storeId;
        private final long giftId;
        private BigDecimal quantity;
        private long version;

        private StockEntry(long id, long storeId, long giftId, BigDecimal quantity, long version) {
            this.id = id;
            this.storeId = storeId;
            this.giftId = giftId;
            this.quantity = quantity;
            this.version = version;
        }
    }

    private static final class MutableTransferLine {
        private final long id;
        private final Gift gift;
        private final BigDecimal quantity;
        private final String note;
        private Long sourceLedgerId;
        private Long targetLedgerId;

        private MutableTransferLine(long id, Gift gift, BigDecimal quantity, String note) {
            this.id = id;
            this.gift = gift;
            this.quantity = quantity;
            this.note = note;
        }

        private TransferLine value() {
            return new TransferLine(id, gift.id(), gift.code(), gift.name(), gift.unitName(),
                    gift.unitDecimalPlaces(), quantity, note, sourceLedgerId, targetLedgerId);
        }
    }

    private static final class TransferEntry {
        private final long id;
        private final String transferNo;
        private final long sourceStoreId;
        private final long targetStoreId;
        private final java.time.LocalDate transferDate;
        private final String remarks;
        private final List<MutableTransferLine> lines;
        private final String idempotencyKey;
        private final LocalDateTime createdAt = LocalDateTime.now();
        private final long createdBy;
        private String status = "DRAFT";
        private LocalDateTime confirmedAt;
        private LocalDateTime voidedAt;
        private LocalDateTime reversedAt;
        private String actionReason;
        private long version = 1;

        private TransferEntry(long id, NewTransfer draft, List<MutableTransferLine> lines) {
            this.id = id;
            this.transferNo = draft.transferNo();
            this.sourceStoreId = draft.sourceStoreId();
            this.targetStoreId = draft.targetStoreId();
            this.transferDate = draft.transferDate();
            this.remarks = draft.remarks();
            this.lines = lines;
            this.idempotencyKey = draft.idempotencyKey();
            this.createdBy = draft.operatorId();
        }

        private boolean sameRequest(NewTransfer draft) {
            if (sourceStoreId != draft.sourceStoreId() || targetStoreId != draft.targetStoreId()
                    || !transferDate.equals(draft.transferDate()) || lines.size() != draft.lines().size()) {
                return false;
            }
            Map<Long, BigDecimal> quantities = new HashMap<>();
            lines.forEach(line -> quantities.put(line.gift.id(), line.quantity));
            return draft.lines().stream().allMatch(line -> line.quantity().compareTo(
                    quantities.getOrDefault(line.giftId(), BigDecimal.valueOf(-1))) == 0);
        }

        private List<MutableTransferLine> sortedLines() {
            return lines.stream().sorted(Comparator.comparingLong(line -> line.gift.id())).toList();
        }

        private String version() { return String.valueOf(version); }

        private TransferSummary summary() {
            return new TransferSummary(
                    id, transferNo, sourceStoreId, storeName(sourceStoreId), targetStoreId,
                    storeName(targetStoreId), transferDate, lines.size(),
                    lines.stream().map(line -> line.quantity).reduce(BigDecimal.ZERO, BigDecimal::add),
                    status, createdAt, createdBy == 1 ? "本地管理员" : "用户" + createdBy, version());
        }

        private TransferDetail detail() {
            return new TransferDetail(
                    id, transferNo, sourceStoreId, storeName(sourceStoreId), targetStoreId,
                    storeName(targetStoreId), transferDate, remarks, status, confirmedAt, voidedAt,
                    reversedAt, actionReason, createdAt,
                    createdBy == 1 ? "本地管理员" : "用户" + createdBy,
                    version(), lines.stream().map(MutableTransferLine::value).toList());
        }
    }

    private static final class MutableCountLine {
        private final long id;
        private final Gift gift;
        private final BigDecimal bookQuantity;
        private BigDecimal actualQuantity;
        private Long stockLedgerId;

        private MutableCountLine(long id, Gift gift, BigDecimal bookQuantity) {
            this.id = id;
            this.gift = gift;
            this.bookQuantity = bookQuantity;
        }

        private CountLine value() {
            BigDecimal difference = actualQuantity == null ? null : actualQuantity.subtract(bookQuantity);
            return new CountLine(
                    id, gift.id(), gift.code(), gift.name(), gift.unitName(), gift.unitDecimalPlaces(),
                    bookQuantity, actualQuantity, difference, stockLedgerId);
        }
    }

    private static final class CountEntry {
        private final long id;
        private final String countNo;
        private final String name;
        private final long storeId;
        private final java.time.LocalDate countDate;
        private final String remarks;
        private final List<MutableCountLine> lines;
        private final String idempotencyKey;
        private final LocalDateTime createdAt = LocalDateTime.now();
        private final long createdBy;
        private String status = "DRAFT";
        private LocalDateTime confirmedAt;
        private LocalDateTime voidedAt;
        private String actionReason;
        private long version = 1;

        private CountEntry(long id, NewCount draft, List<MutableCountLine> lines) {
            this.id = id;
            this.countNo = draft.countNo();
            this.name = draft.name();
            this.storeId = draft.storeId();
            this.countDate = draft.countDate();
            this.remarks = draft.remarks();
            this.lines = lines;
            this.idempotencyKey = draft.idempotencyKey();
            this.createdBy = draft.operatorId();
        }

        private boolean sameRequest(NewCount draft) {
            return storeId == draft.storeId() && name.equals(draft.name()) && countDate.equals(draft.countDate())
                    && new LinkedHashSet<>(draft.giftIds()).equals(lines.stream()
                    .map(line -> line.gift.id()).collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new)));
        }

        private java.util.Set<Long> lineIds() {
            return lines.stream().map(line -> line.id).collect(java.util.stream.Collectors.toSet());
        }

        private List<MutableCountLine> sortedLines() {
            return lines.stream().sorted(Comparator.comparingLong(line -> line.gift.id())).toList();
        }

        private String version() { return String.valueOf(version); }

        private CountSummary summary() {
            List<BigDecimal> differences = lines.stream().map(line -> line.actualQuantity == null
                    ? BigDecimal.ZERO : line.actualQuantity.subtract(line.bookQuantity)).toList();
            return new CountSummary(
                    id, countNo, name, storeId, storeName(storeId), countDate, lines.size(),
                    (int) differences.stream().filter(value -> value.signum() != 0).count(),
                    differences.stream().reduce(BigDecimal.ZERO, BigDecimal::add), status, createdAt,
                    createdBy == 1 ? "本地管理员" : "用户" + createdBy, version());
        }

        private CountDetail detail() {
            return new CountDetail(
                    id, countNo, name, storeId, storeName(storeId), countDate, remarks, status,
                    confirmedAt, voidedAt, actionReason, createdAt,
                    createdBy == 1 ? "本地管理员" : "用户" + createdBy,
                    version(), lines.stream().map(MutableCountLine::value).toList());
        }
    }
}
