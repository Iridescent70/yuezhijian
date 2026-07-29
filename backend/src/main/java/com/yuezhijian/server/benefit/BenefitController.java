package com.yuezhijian.server.benefit;

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
@RequestMapping("/api/v1")
public class BenefitController {
    private final BenefitService service;

    public BenefitController(BenefitService service) { this.service = service; }

    @GetMapping("/vouchers")
    @PreAuthorize("hasAuthority('benefit:voucher:view')")
    public ApiResponse<List<VoucherDefinition>> definitions(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status) {
        return ApiResponse.ok(service.definitions(keyword, status));
    }

    @GetMapping("/vouchers/{id}")
    @PreAuthorize("hasAuthority('benefit:voucher:view')")
    public ApiResponse<VoucherDefinition> definition(@PathVariable long id) {
        return ApiResponse.ok(service.definition(id));
    }

    @PostMapping("/vouchers")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('benefit:voucher:manage')")
    public ApiResponse<VoucherDefinition> create(
            @Valid @RequestBody CreateVoucherDefinitionRequest request, Principal principal) {
        return ApiResponse.ok(service.createDefinition(request, principal.getName()));
    }

    @PutMapping("/vouchers/{id}")
    @PreAuthorize("hasAuthority('benefit:voucher:manage')")
    public ApiResponse<VoucherDefinition> update(
            @PathVariable long id,
            @Valid @RequestBody UpdateVoucherDefinitionRequest request,
            Principal principal) {
        return ApiResponse.ok(service.updateDefinition(id, request, principal.getName()));
    }

    @GetMapping("/voucher-codes")
    @PreAuthorize("hasAuthority('benefit:voucher:view')")
    public ApiResponse<List<VoucherCodeSummary>> voucherCodes(
            @RequestParam(required = false) Long memberId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword) {
        return ApiResponse.ok(service.voucherCodes(memberId, status, keyword));
    }

    @GetMapping("/voucher-codes/{code}")
    @PreAuthorize("hasAuthority('benefit:voucher:view')")
    public ApiResponse<VoucherCodeSummary> voucherCode(@PathVariable String code) {
        return ApiResponse.ok(service.voucherCode(code));
    }

    @PostMapping("/voucher-code-issues")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('benefit:voucher:issue')")
    public ApiResponse<List<VoucherCodeSummary>> issue(
            @Valid @RequestBody IssueVoucherCodesRequest request, Principal principal) {
        return ApiResponse.ok(service.issue(request, principal.getName()));
    }

    @PostMapping("/voucher-codes/{code}/bind")
    @PreAuthorize("hasAuthority('benefit:voucher:issue')")
    public ApiResponse<VoucherCodeSummary> bind(
            @PathVariable String code,
            @Valid @RequestBody BindVoucherCodeRequest request,
            Principal principal) {
        return ApiResponse.ok(service.bind(code, request, principal.getName()));
    }
}
