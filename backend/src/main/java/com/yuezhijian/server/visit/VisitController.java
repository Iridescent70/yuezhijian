package com.yuezhijian.server.visit;

import com.yuezhijian.server.common.ApiResponse;
import jakarta.validation.Valid;
import java.security.Principal;
import java.time.LocalDate;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/visit-tasks")
public class VisitController {
    private final VisitService service;

    public VisitController(VisitService service) { this.service = service; }

    @GetMapping
    @PreAuthorize("hasAuthority('visit:task:view')")
    public ApiResponse<List<VisitTaskSummary>> tasks(
            @RequestParam(required = false) Long storeId,
            @RequestParam(required = false) Long employeeId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) LocalDate dueDate,
            @RequestParam(required = false) String keyword) {
        return ApiResponse.ok(service.tasks(storeId, employeeId, status, dueDate, keyword));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('visit:task:view')")
    public ApiResponse<VisitTaskDetail> detail(@PathVariable long id) {
        return ApiResponse.ok(service.detail(id));
    }

    @PostMapping("/{id}/records")
    @PreAuthorize("hasAuthority('visit:task:manage')")
    public ApiResponse<VisitTaskDetail> addRecord(
            @PathVariable long id,
            @Valid @RequestBody CreateVisitRecordRequest request,
            Principal principal) {
        return ApiResponse.ok(service.addRecord(id, request, principal.getName()));
    }

    @PostMapping("/{id}/complete")
    @PreAuthorize("hasAuthority('visit:task:manage')")
    public ApiResponse<VisitTaskDetail> complete(
            @PathVariable long id,
            @Valid @RequestBody CompleteVisitTaskRequest request,
            Principal principal) {
        return ApiResponse.ok(service.complete(id, request, principal.getName()));
    }
}
