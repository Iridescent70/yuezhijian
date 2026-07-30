package com.yuezhijian.server.cancelreason;

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
@RequestMapping("/api/v1/cancel-reasons")
public class CancelReasonController {
    private final CancelReasonService service;

    public CancelReasonController(CancelReasonService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('system:cancel-reason:view')")
    public ApiResponse<List<CancelReason>> list(
            @RequestParam(required = false) String businessType,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status) {
        return ApiResponse.ok(service.findAll(businessType, keyword, status));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('system:cancel-reason:view')")
    public ApiResponse<CancelReason> detail(@PathVariable long id) {
        return ApiResponse.ok(service.detail(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('system:cancel-reason:manage')")
    public ApiResponse<CancelReason> create(
            @Valid @RequestBody CreateCancelReasonRequest request, Principal principal) {
        return ApiResponse.ok(service.create(request, principal.getName()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('system:cancel-reason:manage')")
    public ApiResponse<CancelReason> update(
            @PathVariable long id,
            @Valid @RequestBody UpdateCancelReasonRequest request,
            Principal principal) {
        return ApiResponse.ok(service.update(id, request, principal.getName()));
    }
}
