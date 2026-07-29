package com.yuezhijian.server.member;

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
public class OwnershipAdjustmentController {
    private final OwnershipAdjustmentService service;

    public OwnershipAdjustmentController(OwnershipAdjustmentService service) {
        this.service = service;
    }

    @GetMapping("/ownership-adjustments")
    @PreAuthorize("hasAuthority('member:ownership:view')")
    public ApiResponse<List<OwnershipAdjustment>> search(
            @RequestParam(required = false) Long memberId,
            @RequestParam(required = false) String approvalStatus,
            @RequestParam(required = false) String executionStatus) {
        return ApiResponse.ok(service.search(memberId, approvalStatus, executionStatus));
    }

    @GetMapping("/ownership-adjustments/{id}")
    @PreAuthorize("hasAuthority('member:ownership:view')")
    public ApiResponse<OwnershipAdjustment> detail(@PathVariable long id) {
        return ApiResponse.ok(service.detail(id));
    }

    @PostMapping("/members/{memberId}/ownership-adjustments")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('member:ownership:manage')")
    public ApiResponse<OwnershipAdjustment> create(
            @PathVariable long memberId,
            @Valid @RequestBody CreateOwnershipAdjustmentRequest request,
            Principal principal) {
        return ApiResponse.ok(service.create(memberId, request, principal.getName()));
    }

    @PostMapping("/ownership-adjustments/{id}/approve")
    @PreAuthorize("hasAuthority('member:ownership:approve')")
    public ApiResponse<OwnershipAdjustment> approve(
            @PathVariable long id,
            @Valid @RequestBody ReviewOwnershipAdjustmentRequest request,
            Principal principal) {
        return ApiResponse.ok(service.review(id, true, request, principal.getName()));
    }

    @PostMapping("/ownership-adjustments/{id}/reject")
    @PreAuthorize("hasAuthority('member:ownership:approve')")
    public ApiResponse<OwnershipAdjustment> reject(
            @PathVariable long id,
            @Valid @RequestBody ReviewOwnershipAdjustmentRequest request,
            Principal principal) {
        return ApiResponse.ok(service.review(id, false, request, principal.getName()));
    }
}
