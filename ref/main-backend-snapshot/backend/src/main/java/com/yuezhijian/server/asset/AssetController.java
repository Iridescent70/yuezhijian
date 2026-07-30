package com.yuezhijian.server.asset;

import com.yuezhijian.server.common.ApiResponse;
import jakarta.validation.Valid;
import java.security.Principal;
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
public class AssetController {
    private final AssetService service;

    public AssetController(AssetService service) {
        this.service = service;
    }

    @GetMapping("/members/{memberId}/balance-account")
    @PreAuthorize("hasAuthority('member:asset:view')")
    public ApiResponse<BalanceAccount> balanceAccount(@PathVariable long memberId) {
        return ApiResponse.ok(service.balanceAccount(memberId));
    }

    @GetMapping("/members/{memberId}/balance-ledgers")
    @PreAuthorize("hasAuthority('member:asset:view')")
    public ApiResponse<List<BalanceLedgerItem>> balanceLedgers(
            @PathVariable long memberId, @RequestParam(defaultValue = "100") int limit) {
        return ApiResponse.ok(service.balanceLedgers(memberId, limit));
    }

    @GetMapping("/members/{memberId}/point-account")
    @PreAuthorize("hasAuthority('member:asset:view')")
    public ApiResponse<PointAccount> pointAccount(@PathVariable long memberId) {
        return ApiResponse.ok(service.pointAccount(memberId));
    }

    @GetMapping("/members/{memberId}/point-ledgers")
    @PreAuthorize("hasAuthority('member:asset:view')")
    public ApiResponse<List<PointLedgerItem>> pointLedgers(
            @PathVariable long memberId, @RequestParam(defaultValue = "100") int limit) {
        return ApiResponse.ok(service.pointLedgers(memberId, limit));
    }

    @PostMapping("/members/{memberId}/recharges/quote")
    @PreAuthorize("hasAuthority('member:asset:manage')")
    public ApiResponse<RechargeQuote> quoteRecharge(
            @PathVariable long memberId,
            @Valid @RequestBody RechargeQuoteRequest request,
            Principal principal) {
        return ApiResponse.ok(service.quoteRecharge(memberId, request, principal.getName()));
    }

    @PostMapping("/members/{memberId}/recharges")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('member:asset:manage')")
    public ApiResponse<RechargeOrder> createRecharge(
            @PathVariable long memberId,
            @Valid @RequestBody CreateRechargeRequest request,
            Principal principal) {
        return ApiResponse.ok(service.createRecharge(memberId, request, principal.getName()));
    }

    @GetMapping("/recharges/{id}")
    @PreAuthorize("hasAuthority('member:asset:view')")
    public ApiResponse<RechargeOrder> rechargeDetail(@PathVariable long id) {
        return ApiResponse.ok(service.rechargeDetail(id));
    }

    @PostMapping("/recharges/{id}/confirm")
    @PreAuthorize("hasAuthority('member:asset:manage')")
    public ApiResponse<RechargeOrder> confirmRecharge(
            @PathVariable long id,
            @Valid @RequestBody RechargeActionRequest request,
            Principal principal) {
        return ApiResponse.ok(service.confirmRecharge(id, request, principal.getName()));
    }

    @PostMapping("/recharges/{id}/cancel")
    @PreAuthorize("hasAuthority('member:asset:manage')")
    public ApiResponse<RechargeOrder> cancelRecharge(
            @PathVariable long id,
            @Valid @RequestBody RechargeActionRequest request,
            Principal principal) {
        return ApiResponse.ok(service.cancelRecharge(id, request, principal.getName()));
    }

    @PostMapping("/members/{memberId}/points/adjustments")
    @PreAuthorize("hasAuthority('member:asset:manage')")
    public ApiResponse<PointAccount> adjustPoints(
            @PathVariable long memberId,
            @Valid @RequestBody PointAdjustmentRequest request,
            Principal principal) {
        return ApiResponse.ok(service.adjustPoints(memberId, request, principal.getName()));
    }
}
