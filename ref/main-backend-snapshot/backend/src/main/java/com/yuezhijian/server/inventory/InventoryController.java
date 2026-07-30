package com.yuezhijian.server.inventory;

import com.yuezhijian.server.common.ApiResponse;
import com.yuezhijian.server.common.PageResult;
import jakarta.validation.Valid;
import java.security.Principal;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class InventoryController {
    private final InventoryService service;

    public InventoryController(InventoryService service) {
        this.service = service;
    }

    @GetMapping("/gifts")
    @PreAuthorize("hasAuthority('inventory:gift:view')")
    public ApiResponse<PageResult<Gift>> gifts(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(service.gifts(keyword, status, page, size));
    }

    @GetMapping("/gifts/{id}")
    @PreAuthorize("hasAuthority('inventory:gift:view')")
    public ApiResponse<Gift> gift(@PathVariable long id) {
        return ApiResponse.ok(service.gift(id));
    }

    @PostMapping("/gifts")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('inventory:gift:manage')")
    public ApiResponse<Gift> createGift(
            @Valid @RequestBody CreateGiftRequest request, Principal principal) {
        return ApiResponse.ok(service.createGift(request, principal.getName()));
    }

    @PutMapping("/gifts/{id}")
    @PreAuthorize("hasAuthority('inventory:gift:manage')")
    public ApiResponse<Gift> updateGift(
            @PathVariable long id, @Valid @RequestBody UpdateGiftRequest request, Principal principal) {
        return ApiResponse.ok(service.updateGift(id, request, principal.getName()));
    }

    @GetMapping("/inventories")
    @PreAuthorize("hasAuthority('inventory:stock:view')")
    public ApiResponse<PageResult<StockItem>> stocks(
            @RequestParam(required = false) Long storeId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Boolean lowStock,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(service.stocks(storeId, keyword, lowStock, page, size));
    }

    @GetMapping("/inventories/{storeId}/gifts/{giftId}/ledgers")
    @PreAuthorize("hasAuthority('inventory:stock:view')")
    public ApiResponse<PageResult<StockLedgerItem>> stockLedgers(
            @PathVariable long storeId,
            @PathVariable long giftId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(service.stockLedgers(storeId, giftId, page, size));
    }

    @GetMapping("/inventory-transfers")
    @PreAuthorize("hasAuthority('inventory:transfer:view')")
    public ApiResponse<PageResult<TransferSummary>> transfers(
            @RequestParam(required = false) Long storeId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(service.transfers(storeId, keyword, status, page, size));
    }

    @GetMapping("/inventory-transfers/{id}")
    @PreAuthorize("hasAuthority('inventory:transfer:view')")
    public ApiResponse<TransferDetail> transfer(@PathVariable long id) {
        return ApiResponse.ok(service.transfer(id));
    }

    @PostMapping("/inventory-transfers")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('inventory:transfer:manage')")
    public ApiResponse<TransferDetail> createTransfer(
            @Valid @RequestBody CreateTransferRequest request, Principal principal) {
        return ApiResponse.ok(service.createTransfer(request, principal.getName()));
    }

    @PostMapping("/inventory-transfers/{id}/confirm")
    @PreAuthorize("hasAuthority('inventory:transfer:manage')")
    public ApiResponse<TransferDetail> confirmTransfer(
            @PathVariable long id, @Valid @RequestBody ConfirmInventoryRequest request, Principal principal) {
        return ApiResponse.ok(service.confirmTransfer(id, request, principal.getName()));
    }

    @PostMapping("/inventory-transfers/{id}/void")
    @PreAuthorize("hasAuthority('inventory:transfer:manage')")
    public ApiResponse<TransferDetail> voidTransfer(
            @PathVariable long id, @Valid @RequestBody InventoryActionRequest request, Principal principal) {
        return ApiResponse.ok(service.voidTransfer(id, request, principal.getName()));
    }

    @PostMapping("/inventory-transfers/{id}/reverse")
    @PreAuthorize("hasAuthority('inventory:transfer:manage')")
    public ApiResponse<TransferDetail> reverseTransfer(
            @PathVariable long id, @Valid @RequestBody InventoryActionRequest request, Principal principal) {
        return ApiResponse.ok(service.reverseTransfer(id, request, principal.getName()));
    }

    @GetMapping("/inventory-counts")
    @PreAuthorize("hasAuthority('inventory:count:view')")
    public ApiResponse<PageResult<CountSummary>> counts(
            @RequestParam(required = false) Long storeId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(service.counts(storeId, keyword, status, page, size));
    }

    @GetMapping("/inventory-counts/{id}")
    @PreAuthorize("hasAuthority('inventory:count:view')")
    public ApiResponse<CountDetail> count(@PathVariable long id) {
        return ApiResponse.ok(service.count(id));
    }

    @PostMapping("/inventory-counts")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('inventory:count:manage')")
    public ApiResponse<CountDetail> createCount(
            @Valid @RequestBody CreateCountRequest request, Principal principal) {
        return ApiResponse.ok(service.createCount(request, principal.getName()));
    }

    @PutMapping("/inventory-counts/{id}/lines")
    @PreAuthorize("hasAuthority('inventory:count:manage')")
    public ApiResponse<CountDetail> saveCountLines(
            @PathVariable long id, @Valid @RequestBody SaveCountLinesRequest request, Principal principal) {
        return ApiResponse.ok(service.saveCountLines(id, request, principal.getName()));
    }

    @PostMapping("/inventory-counts/{id}/confirm")
    @PreAuthorize("hasAuthority('inventory:count:manage')")
    public ApiResponse<CountDetail> confirmCount(
            @PathVariable long id, @Valid @RequestBody ConfirmInventoryRequest request, Principal principal) {
        return ApiResponse.ok(service.confirmCount(id, request, principal.getName()));
    }

    @PostMapping("/inventory-counts/{id}/void")
    @PreAuthorize("hasAuthority('inventory:count:manage')")
    public ApiResponse<CountDetail> voidCount(
            @PathVariable long id, @Valid @RequestBody InventoryActionRequest request, Principal principal) {
        return ApiResponse.ok(service.voidCount(id, request, principal.getName()));
    }
}
