package com.yuezhijian.server.inventory;

import com.yuezhijian.server.common.DuplicateResourceException;
import com.yuezhijian.server.common.PageResult;
import com.yuezhijian.server.common.ResourceNotFoundException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Profile("sqlserver")
public class SqlServerInventoryRepository implements InventoryRepository {
    private final InventoryMapper mapper;
    private final InventoryNumberGenerator numbers;

    public SqlServerInventoryRepository(InventoryMapper mapper, InventoryNumberGenerator numbers) {
        this.mapper = mapper;
        this.numbers = numbers;
    }

    @Override
    public PageResult<Gift> gifts(String keyword, String status, int page, int size) {
        int offset = (page - 1) * size;
        return new PageResult<>(
                mapper.findGifts(keyword, status, offset, size), page, size, mapper.countGifts(keyword, status));
    }

    @Override
    public Optional<Gift> findGift(long id) {
        return Optional.ofNullable(mapper.findGift(id));
    }

    @Override
    public Optional<Gift> findGiftByCode(String code) {
        return Optional.ofNullable(mapper.findGiftByCode(code));
    }

    @Override
    @Transactional
    public Gift createGift(NewGift gift) {
        try {
            return findGift(mapper.insertGift(gift)).orElseThrow();
        } catch (DataIntegrityViolationException exception) {
            throw new DuplicateResourceException("礼品编号已存在或基础资料已变化");
        }
    }

    @Override
    @Transactional
    public Gift updateGift(GiftUpdate update) {
        if (mapper.updateGift(update) == 0) {
            if (mapper.findGift(update.id()) == null) throw new ResourceNotFoundException("礼品不存在");
            throw new DuplicateResourceException("礼品资料已被他人修改，请刷新后重试");
        }
        return findGift(update.id()).orElseThrow();
    }

    @Override
    public PageResult<StockItem> stocks(
            long storeId, String keyword, Boolean lowStock, int page, int size) {
        int offset = (page - 1) * size;
        return new PageResult<>(mapper.findStocks(storeId, keyword, lowStock, offset, size), page, size,
                mapper.countStocks(storeId, keyword, lowStock));
    }

    @Override
    public PageResult<StockLedgerItem> stockLedgers(long storeId, long giftId, int page, int size) {
        int offset = (page - 1) * size;
        return new PageResult<>(mapper.findStockLedgers(storeId, giftId, offset, size), page, size,
                mapper.countStockLedgers(storeId, giftId));
    }

    @Override
    public PageResult<TransferSummary> transfers(
            Long storeId, String keyword, String status, int page, int size) {
        int offset = (page - 1) * size;
        return new PageResult<>(mapper.findTransfers(storeId, keyword, status, offset, size), page, size,
                mapper.countTransfers(storeId, keyword, status));
    }

    @Override
    public Optional<TransferDetail> findTransfer(long id) {
        return Optional.ofNullable(mapper.findTransfer(id)).map(this::transferDetail);
    }

    @Override
    @Transactional
    public TransferDetail createTransfer(NewTransfer transfer) {
        TransferHeaderRow existing = mapper.findTransferByIdempotencyKey(transfer.idempotencyKey());
        if (existing != null) return requireSame(existing, transfer);
        try {
            long id = mapper.insertTransfer(transfer);
            transfer.lines().forEach(line -> mapper.insertTransferLine(id, line));
            return findTransfer(id).orElseThrow();
        } catch (DataIntegrityViolationException exception) {
            TransferHeaderRow raced = mapper.findTransferByIdempotencyKey(transfer.idempotencyKey());
            if (raced != null) return requireSame(raced, transfer);
            throw new DuplicateResourceException("调拨单与现有数据冲突");
        }
    }

    @Override
    @Transactional
    public TransferDetail confirmTransfer(long id, String version, String reason, long operatorId) {
        TransferDetail current = requireTransfer(id);
        if ("CONFIRMED".equals(current.status())) return current;
        requireStatus(current.status(), "DRAFT", "只有草稿调拨单可以确认");
        Map<StockKey, StockLockRow> stocks = lockStocks(transferKeys(current), operatorId);
        for (TransferLine line : sorted(current.lines())) {
            StockLockRow source = stocks.get(new StockKey(current.sourceStoreId(), line.giftId()));
            if (source.onHandQuantity().compareTo(line.quantity()) < 0) {
                throw new IllegalArgumentException(line.giftName() + "库存不足");
            }
        }
        LocalDateTime now = LocalDateTime.now();
        for (TransferLine line : sorted(current.lines())) {
            StockKey sourceKey = new StockKey(current.sourceStoreId(), line.giftId());
            StockKey targetKey = new StockKey(current.targetStoreId(), line.giftId());
            StockMutation source = mutate(stocks.get(sourceKey), line.quantity().negate(), "TRANSFER_OUT",
                    "TRANSFER", current.id(), line.id(), null, current.transferNo(), now, operatorId);
            stocks.put(sourceKey, source.stock());
            StockMutation target = mutate(stocks.get(targetKey), line.quantity(), "TRANSFER_IN",
                    "TRANSFER", current.id(), line.id(), null, current.transferNo(), now, operatorId);
            stocks.put(targetKey, target.stock());
            if (mapper.linkTransferLedgers(line.id(), source.ledgerId(), target.ledgerId()) != 1) {
                throw new DuplicateResourceException("调拨明细已产生库存流水，请刷新后重试");
            }
        }
        updateTransferStatus(id, "DRAFT", "CONFIRMED", reason, now, operatorId, version);
        return requireTransfer(id);
    }

    @Override
    @Transactional
    public TransferDetail voidTransfer(long id, String version, String reason, long operatorId) {
        TransferDetail current = requireTransfer(id);
        if ("VOIDED".equals(current.status())) return current;
        requireStatus(current.status(), "DRAFT", "只有草稿调拨单可以作废");
        updateTransferStatus(id, "DRAFT", "VOIDED", reason, LocalDateTime.now(), operatorId, version);
        return requireTransfer(id);
    }

    @Override
    @Transactional
    public TransferDetail reverseTransfer(long id, String version, String reason, long operatorId) {
        TransferDetail current = requireTransfer(id);
        if ("REVERSED".equals(current.status())) return current;
        requireStatus(current.status(), "CONFIRMED", "只有已确认调拨单可以冲销");
        Map<StockKey, StockLockRow> stocks = lockStocks(transferKeys(current), operatorId);
        for (TransferLine line : sorted(current.lines())) {
            StockLockRow target = stocks.get(new StockKey(current.targetStoreId(), line.giftId()));
            if (target.onHandQuantity().compareTo(line.quantity()) < 0) {
                throw new IllegalArgumentException(line.giftName() + "调入门店库存不足，不能冲销");
            }
            if (line.sourceLedgerId() == null || line.targetLedgerId() == null) {
                throw new IllegalStateException("已确认调拨单缺少原始库存流水");
            }
        }
        LocalDateTime now = LocalDateTime.now();
        for (TransferLine line : sorted(current.lines())) {
            StockKey sourceKey = new StockKey(current.sourceStoreId(), line.giftId());
            StockKey targetKey = new StockKey(current.targetStoreId(), line.giftId());
            StockMutation target = mutate(stocks.get(targetKey), line.quantity().negate(),
                    "TRANSFER_REVERSAL_OUT", "TRANSFER_REVERSAL", current.id(), line.id(),
                    line.targetLedgerId(), reason, now, operatorId);
            stocks.put(targetKey, target.stock());
            StockMutation source = mutate(stocks.get(sourceKey), line.quantity(),
                    "TRANSFER_REVERSAL_IN", "TRANSFER_REVERSAL", current.id(), line.id(),
                    line.sourceLedgerId(), reason, now, operatorId);
            stocks.put(sourceKey, source.stock());
        }
        updateTransferStatus(id, "CONFIRMED", "REVERSED", reason, now, operatorId, version);
        return requireTransfer(id);
    }

    @Override
    public PageResult<CountSummary> counts(
            Long storeId, String keyword, String status, int page, int size) {
        int offset = (page - 1) * size;
        return new PageResult<>(mapper.findCounts(storeId, keyword, status, offset, size), page, size,
                mapper.countCounts(storeId, keyword, status));
    }

    @Override
    public Optional<CountDetail> findCount(long id) {
        return Optional.ofNullable(mapper.findCount(id)).map(this::countDetail);
    }

    @Override
    @Transactional
    public CountDetail createCount(NewCount count) {
        CountHeaderRow existing = mapper.findCountByIdempotencyKey(count.idempotencyKey());
        if (existing != null) return requireSame(existing, count);
        Map<StockKey, StockLockRow> stocks = lockStocks(count.giftIds().stream()
                .map(giftId -> new StockKey(count.storeId(), giftId)).collect(java.util.stream.Collectors.toSet()),
                count.operatorId());
        try {
            long id = mapper.insertCount(count);
            count.giftIds().forEach(giftId -> mapper.insertCountLine(
                    id, giftId, stocks.get(new StockKey(count.storeId(), giftId)).onHandQuantity()));
            return findCount(id).orElseThrow();
        } catch (DataIntegrityViolationException exception) {
            CountHeaderRow raced = mapper.findCountByIdempotencyKey(count.idempotencyKey());
            if (raced != null) return requireSame(raced, count);
            throw new DuplicateResourceException("盘点单与现有数据冲突");
        }
    }

    @Override
    @Transactional
    public CountDetail saveCountLines(
            long id, String version, List<CountLineInput> inputs, long operatorId) {
        CountDetail current = requireCount(id);
        requireOneOf(current.status(), List.of("DRAFT", "READY_CONFIRM"), "当前盘点单不能录入实盘数");
        Set<Long> expected = current.lines().stream().map(CountLine::id).collect(java.util.stream.Collectors.toSet());
        Set<Long> actual = inputs.stream().map(CountLineInput::lineId)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        if (actual.size() != inputs.size() || !expected.equals(actual)) {
            throw new IllegalArgumentException("必须提交盘点单全部且不重复的明细");
        }
        inputs.forEach(input -> {
            if (mapper.updateCountLine(id, input.lineId(), input.actualQuantity()) != 1) {
                throw new DuplicateResourceException("盘点明细已变化，请刷新后重试");
            }
        });
        updateCountStatus(id, List.of("DRAFT", "READY_CONFIRM"), "READY_CONFIRM", null,
                LocalDateTime.now(), operatorId, version);
        return requireCount(id);
    }

    @Override
    @Transactional
    public CountDetail confirmCount(long id, String version, String reason, long operatorId) {
        CountDetail current = requireCount(id);
        if ("CONFIRMED".equals(current.status())) return current;
        requireStatus(current.status(), "READY_CONFIRM", "盘点明细未全部录入，不能确认");
        if (current.lines().stream().anyMatch(line -> line.actualQuantity() == null)) {
            throw new IllegalArgumentException("盘点明细未全部录入，不能确认");
        }
        Set<StockKey> keys = current.lines().stream()
                .map(line -> new StockKey(current.storeId(), line.giftId()))
                .collect(java.util.stream.Collectors.toSet());
        Map<StockKey, StockLockRow> stocks = lockStocks(keys, operatorId);
        for (CountLine line : current.lines()) {
            StockLockRow stock = stocks.get(new StockKey(current.storeId(), line.giftId()));
            if (stock.onHandQuantity().compareTo(line.bookQuantity()) != 0) {
                throw new DuplicateResourceException(line.giftName() + "账面库存已变化，请作废本单后重新盘点");
            }
        }
        LocalDateTime now = LocalDateTime.now();
        for (CountLine line : current.lines().stream().sorted(Comparator.comparingLong(CountLine::giftId)).toList()) {
            BigDecimal change = line.actualQuantity().subtract(line.bookQuantity());
            if (change.signum() == 0) continue;
            StockKey key = new StockKey(current.storeId(), line.giftId());
            StockMutation mutation = mutate(stocks.get(key), change,
                    change.signum() > 0 ? "COUNT_GAIN" : "COUNT_LOSS", "COUNT",
                    current.id(), line.id(), null, reason, now, operatorId);
            stocks.put(key, mutation.stock());
            if (mapper.linkCountLedger(line.id(), mutation.ledgerId()) != 1) {
                throw new DuplicateResourceException("盘点明细已产生库存流水，请刷新后重试");
            }
        }
        updateCountStatus(id, List.of("READY_CONFIRM"), "CONFIRMED", reason, now, operatorId, version);
        return requireCount(id);
    }

    @Override
    @Transactional
    public CountDetail voidCount(long id, String version, String reason, long operatorId) {
        CountDetail current = requireCount(id);
        if ("VOIDED".equals(current.status())) return current;
        requireOneOf(current.status(), List.of("DRAFT", "READY_CONFIRM"), "当前盘点单不能作废");
        updateCountStatus(id, List.of("DRAFT", "READY_CONFIRM"), "VOIDED", reason,
                LocalDateTime.now(), operatorId, version);
        return requireCount(id);
    }

    private TransferDetail transferDetail(TransferHeaderRow row) {
        return new TransferDetail(
                row.id(), row.transferNo(), row.sourceStoreId(), row.sourceStoreName(), row.targetStoreId(),
                row.targetStoreName(), row.transferDate(), row.remarks(), row.status(), row.confirmedAt(),
                row.voidedAt(), row.reversedAt(), row.actionReason(), row.createdAt(), row.createdByName(),
                row.version(), mapper.findTransferLines(row.id()));
    }

    private CountDetail countDetail(CountHeaderRow row) {
        return new CountDetail(
                row.id(), row.countNo(), row.name(), row.storeId(), row.storeName(), row.countDate(),
                row.remarks(), row.status(), row.confirmedAt(), row.voidedAt(), row.actionReason(),
                row.createdAt(), row.createdByName(), row.version(), mapper.findCountLines(row.id()));
    }

    private TransferDetail requireTransfer(long id) {
        return findTransfer(id).orElseThrow(() -> new ResourceNotFoundException("调拨单不存在"));
    }

    private CountDetail requireCount(long id) {
        return findCount(id).orElseThrow(() -> new ResourceNotFoundException("盘点单不存在"));
    }

    private TransferDetail requireSame(TransferHeaderRow existing, NewTransfer draft) {
        TransferDetail detail = transferDetail(existing);
        boolean sameLines = detail.lines().size() == draft.lines().size();
        if (sameLines) {
            Map<Long, BigDecimal> existingQuantities = new LinkedHashMap<>();
            detail.lines().forEach(line -> existingQuantities.put(line.giftId(), line.quantity()));
            sameLines = draft.lines().stream().allMatch(line -> line.quantity().compareTo(
                    existingQuantities.getOrDefault(line.giftId(), BigDecimal.valueOf(-1))) == 0);
        }
        if (existing.sourceStoreId() != draft.sourceStoreId()
                || existing.targetStoreId() != draft.targetStoreId()
                || !existing.transferDate().equals(draft.transferDate()) || !sameLines) {
            throw new DuplicateResourceException("幂等键已用于其他调拨单");
        }
        return detail;
    }

    private CountDetail requireSame(CountHeaderRow existing, NewCount draft) {
        CountDetail detail = countDetail(existing);
        Set<Long> existingGifts = detail.lines().stream().map(CountLine::giftId)
                .collect(java.util.stream.Collectors.toSet());
        if (existing.storeId() != draft.storeId() || !existing.countDate().equals(draft.countDate())
                || !existing.name().equals(draft.name())
                || !existingGifts.equals(new LinkedHashSet<>(draft.giftIds()))) {
            throw new DuplicateResourceException("幂等键已用于其他盘点单");
        }
        return detail;
    }

    private Map<StockKey, StockLockRow> lockStocks(Set<StockKey> keys, long operatorId) {
        List<StockKey> ordered = keys.stream().sorted(Comparator.comparingLong(StockKey::storeId)
                .thenComparingLong(StockKey::giftId)).toList();
        ordered.forEach(key -> mapper.ensureStock(key.storeId(), key.giftId(), operatorId));
        Map<StockKey, StockLockRow> result = new LinkedHashMap<>();
        ordered.forEach(key -> {
            StockLockRow stock = mapper.lockStock(key.storeId(), key.giftId());
            if (stock == null) throw new IllegalStateException("库存行初始化失败");
            result.put(key, stock);
        });
        return result;
    }

    private Set<StockKey> transferKeys(TransferDetail transfer) {
        Set<StockKey> keys = new LinkedHashSet<>();
        transfer.lines().forEach(line -> {
            keys.add(new StockKey(transfer.sourceStoreId(), line.giftId()));
            keys.add(new StockKey(transfer.targetStoreId(), line.giftId()));
        });
        return keys;
    }

    private StockMutation mutate(
            StockLockRow stock, BigDecimal change, String transactionType, String sourceType,
            long sourceId, Long sourceLineId, Long reversedLedgerId, String note,
            LocalDateTime occurredAt, long operatorId) {
        BigDecimal after = stock.onHandQuantity().add(change);
        if (after.signum() < 0) throw new IllegalArgumentException("库存不足");
        if (mapper.updateStock(stock.id(), after, occurredAt, operatorId, stock.rowVersion()) != 1) {
            throw new DuplicateResourceException("库存已被其他操作修改，请刷新后重试");
        }
        long ledgerId = mapper.insertStockLedger(
                numbers.ledgerNo(), stock.storeId(), stock.giftId(), transactionType,
                stock.onHandQuantity(), change, after, sourceType, sourceId, sourceLineId,
                occurredAt, reversedLedgerId, note, operatorId);
        StockLockRow updated = mapper.lockStock(stock.storeId(), stock.giftId());
        return new StockMutation(updated, ledgerId);
    }

    private void updateTransferStatus(
            long id, String expected, String target, String reason, LocalDateTime occurredAt,
            long operatorId, String version) {
        if (mapper.updateTransferStatus(id, expected, target, reason, occurredAt, operatorId, version) != 1) {
            throw staleTransfer(id);
        }
    }

    private void updateCountStatus(
            long id, List<String> expected, String target, String reason, LocalDateTime occurredAt,
            long operatorId, String version) {
        if (mapper.updateCountStatus(id, expected, target, reason, occurredAt, operatorId, version) != 1) {
            if (mapper.findCount(id) == null) throw new ResourceNotFoundException("盘点单不存在");
            throw new DuplicateResourceException("盘点单状态或版本已变化，请刷新后重试");
        }
    }

    private RuntimeException staleTransfer(long id) {
        if (mapper.findTransfer(id) == null) return new ResourceNotFoundException("调拨单不存在");
        return new DuplicateResourceException("调拨单状态或版本已变化，请刷新后重试");
    }

    private static void requireStatus(String current, String expected, String message) {
        if (!expected.equals(current)) throw new IllegalArgumentException(message);
    }

    private static void requireOneOf(String current, List<String> expected, String message) {
        if (!expected.contains(current)) throw new IllegalArgumentException(message);
    }

    private static List<TransferLine> sorted(List<TransferLine> lines) {
        return lines.stream().sorted(Comparator.comparingLong(TransferLine::giftId)).toList();
    }

    private record StockKey(long storeId, long giftId) {}
    private record StockMutation(StockLockRow stock, long ledgerId) {}
}
