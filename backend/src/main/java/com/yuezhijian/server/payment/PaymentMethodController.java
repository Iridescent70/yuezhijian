package com.yuezhijian.server.payment;

import com.yuezhijian.server.common.ApiResponse;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;
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
@RequestMapping("/api/v1/payment-methods")
public class PaymentMethodController {
    private final PaymentMethodService service;

    public PaymentMethodController(PaymentMethodService service) {
        this.service = service;
    }

    @GetMapping("/management")
    @PreAuthorize("hasAuthority('catalog:payment:view')")
    public ApiResponse<List<PaymentMethodConfiguration>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long storeId) {
        return ApiResponse.ok(service.configurations(keyword, type, status, storeId));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('catalog:payment:view')")
    public ApiResponse<PaymentMethodConfiguration> detail(
            @PathVariable long id, @RequestParam(required = false) Long storeId) {
        return ApiResponse.ok(service.detail(id, storeId));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('catalog:payment:manage')")
    public ApiResponse<PaymentMethodConfiguration> create(
            @Valid @RequestBody CreatePaymentMethodRequest request, Principal principal) {
        return ApiResponse.ok(service.create(request, principal.getName()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('catalog:payment:manage')")
    public ApiResponse<PaymentMethodConfiguration> update(
            @PathVariable long id,
            @Valid @RequestBody UpdatePaymentMethodRequest request,
            Principal principal) {
        return ApiResponse.ok(service.update(id, request, principal.getName()));
    }

    @PutMapping("/{id}/stores/{storeId}")
    @PreAuthorize("hasAuthority('catalog:payment:store-manage')")
    public ApiResponse<PaymentMethodConfiguration> configureStore(
            @PathVariable long id,
            @PathVariable long storeId,
            @Valid @RequestBody UpdatePaymentMethodStoreRequest request,
            Principal principal) {
        return ApiResponse.ok(service.configureStore(id, storeId, request, principal.getName()));
    }

    @PutMapping("/sort")
    @PreAuthorize("hasAuthority('catalog:payment:store-manage')")
    public ApiResponse<List<PaymentMethodConfiguration>> reorder(
            @Valid @RequestBody SortPaymentMethodsRequest request, Principal principal) {
        return ApiResponse.ok(service.reorder(request, principal.getName()));
    }
}
