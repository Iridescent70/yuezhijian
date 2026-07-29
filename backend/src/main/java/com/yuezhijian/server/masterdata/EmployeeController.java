package com.yuezhijian.server.masterdata;

import com.yuezhijian.server.common.ApiResponse;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class EmployeeController {
    private final MasterDataService service;

    public EmployeeController(MasterDataService service) {
        this.service = service;
    }

    @GetMapping("/positions")
    @PreAuthorize("hasAuthority('org:employee:view')")
    public ApiResponse<List<PositionOption>> positions() {
        return ApiResponse.ok(service.positions());
    }

    @GetMapping("/employees")
    @PreAuthorize("hasAuthority('org:employee:view')")
    public ApiResponse<List<EmployeeSummary>> employees(
            @RequestParam(required = false) Long storeId,
            @RequestParam(required = false) String keyword) {
        return ApiResponse.ok(service.employees(storeId, keyword));
    }

    @PostMapping("/employees")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('org:employee:manage')")
    public ApiResponse<CreatedResource> createEmployee(
            @Valid @RequestBody CreateEmployeeRequest request,
            Principal principal) {
        return ApiResponse.ok(service.createEmployee(request, principal.getName()));
    }
}
