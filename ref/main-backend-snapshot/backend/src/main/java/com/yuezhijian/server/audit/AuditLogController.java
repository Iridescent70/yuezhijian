package com.yuezhijian.server.audit;

import com.yuezhijian.server.common.ApiResponse;
import com.yuezhijian.server.common.PageResult;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/audit-logs")
@PreAuthorize("hasAuthority('system:audit:view')")
public class AuditLogController {
    private final AuditService service;

    public AuditLogController(AuditService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<PageResult<AuditLogSummary>> search(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String operator,
            @RequestParam(required = false) String module,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String objectType,
            @RequestParam(required = false) String objectId,
            @RequestParam(required = false) String result,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate occurredFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate occurredTo,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(service.search(
                userId, operator, module, action, objectType, objectId, result,
                occurredFrom, occurredTo, page, size));
    }

    @GetMapping("/{id}")
    public ApiResponse<AuditLogDetail> detail(@PathVariable long id) {
        return ApiResponse.ok(service.detail(id));
    }
}
