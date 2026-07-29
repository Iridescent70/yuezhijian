package com.yuezhijian.server.trade;

import com.yuezhijian.server.common.ApiResponse;
import jakarta.validation.Valid;
import java.security.Principal;
import java.time.LocalDate;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class TradeController {
    private final TradeService service;

    public TradeController(TradeService service) { this.service = service; }

    @GetMapping("/payment-methods")
    @PreAuthorize("hasAuthority('trade:bill:view')")
    public ApiResponse<List<PaymentMethodOption>> paymentMethods(@RequestParam long storeId) {
        return ApiResponse.ok(service.paymentMethods(storeId));
    }

    @GetMapping("/bills")
    @PreAuthorize("hasAuthority('trade:bill:view')")
    public ApiResponse<List<BillSummary>> bills(
            @RequestParam(required = false) Long storeId,
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword) {
        return ApiResponse.ok(service.search(storeId, startDate, endDate, status, keyword));
    }

    @GetMapping("/bills/{id}")
    @PreAuthorize("hasAuthority('trade:bill:view')")
    public ApiResponse<BillDetail> detail(@PathVariable long id) { return ApiResponse.ok(service.detail(id)); }

    @GetMapping("/bills/{id}/card-options")
    @PreAuthorize("hasAuthority('trade:bill:settle')")
    public ApiResponse<List<CardSettlementOption>> cardOptions(@PathVariable long id) {
        return ApiResponse.ok(service.cardOptions(id));
    }

    @GetMapping("/bills/{id}/asset-options")
    @PreAuthorize("hasAuthority('trade:bill:settle')")
    public ApiResponse<SettlementAssetOptions> assetOptions(@PathVariable long id) {
        return ApiResponse.ok(service.assetOptions(id));
    }

    @PostMapping("/bills")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('trade:bill:create')")
    public ApiResponse<CreatedBill> create(@Valid @RequestBody CreateBillRequest request, Principal principal) {
        return ApiResponse.ok(service.create(request, principal.getName()));
    }

    @PostMapping("/appointments/{id}/create-bill")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('trade:bill:create')")
    public ApiResponse<CreatedBill> createFromAppointment(
            @PathVariable long id,
            @RequestBody CreateBillFromAppointmentRequest request,
            Principal principal) {
        return ApiResponse.ok(service.createFromAppointment(id, request, principal.getName()));
    }

    @PostMapping("/bills/{id}/lines")
    @PreAuthorize("hasAuthority('trade:bill:manage')")
    public ApiResponse<BillDetail> addLine(
            @PathVariable long id, @Valid @RequestBody AddBillLineRequest request, Principal principal) {
        return ApiResponse.ok(service.addLine(id, request, principal.getName()));
    }

    @PostMapping("/bills/{id}/settlement/quote")
    @PreAuthorize("hasAuthority('trade:bill:settle')")
    public ApiResponse<SettlementQuote> quote(
            @PathVariable long id, @Valid @RequestBody SettlementQuoteRequest request, Principal principal) {
        return ApiResponse.ok(service.quote(id, request, principal.getName()));
    }

    @PostMapping("/bills/{id}/settle")
    @PreAuthorize("hasAuthority('trade:bill:settle')")
    public ApiResponse<BillDetail> settle(
            @PathVariable long id, @Valid @RequestBody SettleBillRequest request, Principal principal) {
        return ApiResponse.ok(service.settle(id, request, principal.getName()));
    }

    @PostMapping("/bills/{id}/void")
    @PreAuthorize("hasAuthority('trade:bill:manage')")
    public ApiResponse<BillDetail> voidBill(
            @PathVariable long id, @Valid @RequestBody VoidBillRequest request, Principal principal) {
        return ApiResponse.ok(service.voidBill(id, request, principal.getName()));
    }
}
