package com.yuezhijian.server.masterdata;

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
@RequestMapping("/api/v1/workstations")
public class WorkstationController {
    private final MasterDataService service;

    public WorkstationController(MasterDataService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('org:workstation:view')")
    public ApiResponse<List<WorkstationSummary>> list(@RequestParam(required = false) Long storeId) {
        return ApiResponse.ok(service.workstations(storeId));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('org:workstation:view')")
    public ApiResponse<WorkstationSummary> detail(@PathVariable long id) {
        return ApiResponse.ok(service.workstation(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('org:workstation:manage')")
    public ApiResponse<CreatedResource> create(
            @Valid @RequestBody CreateWorkstationRequest request,
            Principal principal) {
        return ApiResponse.ok(service.createWorkstation(request, principal.getName()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('org:workstation:manage')")
    public ApiResponse<WorkstationSummary> update(
            @PathVariable long id,
            @Valid @RequestBody UpdateWorkstationRequest request,
            Principal principal) {
        return ApiResponse.ok(service.updateWorkstation(id, request, principal.getName()));
    }
}
