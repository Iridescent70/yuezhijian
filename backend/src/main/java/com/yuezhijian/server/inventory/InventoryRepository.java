package com.yuezhijian.server.inventory;

import com.yuezhijian.server.common.PageResult;
import java.util.List;
import java.util.Optional;

public interface InventoryRepository {
    PageResult<Gift> gifts(String keyword, String status, int page, int size);
    Optional<Gift> findGift(long id);
    Optional<Gift> findGiftByCode(String code);
    Gift createGift(NewGift gift);
    Gift updateGift(GiftUpdate update);

    PageResult<StockItem> stocks(long storeId, String keyword, Boolean lowStock, int page, int size);
    PageResult<StockLedgerItem> stockLedgers(long storeId, long giftId, int page, int size);

    PageResult<TransferSummary> transfers(Long storeId, String keyword, String status, int page, int size);
    Optional<TransferDetail> findTransfer(long id);
    TransferDetail createTransfer(NewTransfer transfer);
    TransferDetail confirmTransfer(long id, String version, String reason, long operatorId);
    TransferDetail voidTransfer(long id, String version, String reason, long operatorId);
    TransferDetail reverseTransfer(long id, String version, String reason, long operatorId);

    PageResult<CountSummary> counts(Long storeId, String keyword, String status, int page, int size);
    Optional<CountDetail> findCount(long id);
    CountDetail createCount(NewCount count);
    CountDetail saveCountLines(long id, String version, List<CountLineInput> lines, long operatorId);
    CountDetail confirmCount(long id, String version, String reason, long operatorId);
    CountDetail voidCount(long id, String version, String reason, long operatorId);
}
