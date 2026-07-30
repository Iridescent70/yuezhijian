package com.yuezhijian.server.commission;

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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class CommissionController {
    private final CommissionService service;

    public CommissionController(CommissionService service) { this.service = service; }

    @GetMapping("/commission-plans")
    @PreAuthorize("hasAuthority('commission:plan:view')")
    public ApiResponse<List<CommissionPlan>> plans(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status) {
        return ApiResponse.ok(service.plans(keyword, status));
    }

    @GetMapping("/commission-plans/{id}")
    @PreAuthorize("hasAuthority('commission:plan:view')")
    public ApiResponse<CommissionPlan> plan(@PathVariable long id) {
        return ApiResponse.ok(service.plan(id));
    }

    @PostMapping("/commission-plans")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('commission:plan:manage')")
    public ApiResponse<CommissionPlan> create(
            @Valid @RequestBody CreateCommissionPlanRequest request, Principal principal) {
        return ApiResponse.ok(service.createPlan(request, principal.getName()));
    }

    @PutMapping("/commission-plans/{id}")
    @PreAuthorize("hasAuthority('commission:plan:manage')")
    public ApiResponse<CommissionPlan> update(
            @PathVariable long id,
            @Valid @RequestBody UpdateCommissionPlanRequest request,
            Principal principal) {
        return ApiResponse.ok(service.updatePlan(id, request, principal.getName()));
    }

    @PostMapping("/commission-plans/{id}/simulate")
    @PreAuthorize("hasAuthority('commission:plan:view')")
    public ApiResponse<CommissionSimulationResult> simulate(
            @PathVariable long id,
            @Valid @RequestBody SimulateCommissionPlanRequest request) {
        return ApiResponse.ok(service.simulate(id, request));
    }

    @GetMapping("/commission-ledgers")
    @PreAuthorize("hasAuthority('commission:ledger:view')")
    public ApiResponse<List<CommissionLedgerItem>> ledgers(
            @RequestParam(required = false) Long employeeId,
            @RequestParam(required = false) Long storeId,
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate,
            @RequestParam(required = false) String direction,
            @RequestParam(required = false) String calculationStatus) {
        return ApiResponse.ok(service.ledgers(
                employeeId, storeId, startDate, endDate, direction, calculationStatus));
    }
}
