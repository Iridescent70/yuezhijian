package com.yuezhijian.server.trade;

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
public class ReversalController {
    private final ReversalService service;

    public ReversalController(ReversalService service) { this.service = service; }

    @GetMapping("/reversals")
    @PreAuthorize("hasAuthority('trade:reversal:view')")
    public ApiResponse<List<ReversalSummary>> search(@RequestParam(required = false) String status) {
        return ApiResponse.ok(service.search(status));
    }

    @GetMapping("/reversals/{id}")
    @PreAuthorize("hasAuthority('trade:reversal:view')")
    public ApiResponse<ReversalDetail> detail(@PathVariable long id) {
        return ApiResponse.ok(service.detail(id));
    }

    @PostMapping("/bills/{billId}/reversals")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('trade:reversal:manage')")
    public ApiResponse<ReversalDetail> create(
            @PathVariable long billId,
            @Valid @RequestBody CreateReversalRequest request,
            Principal principal) {
        return ApiResponse.ok(service.create(billId, request, principal.getName()));
    }

    @PostMapping("/reversals/{id}/review")
    @PreAuthorize("hasAuthority('trade:reversal:approve')")
    public ApiResponse<ReversalDetail> review(
            @PathVariable long id,
            @Valid @RequestBody ReviewReversalRequest request,
            Principal principal) {
        return ApiResponse.ok(service.review(id, request, principal.getName()));
    }

    @PostMapping("/reversals/{id}/execute")
    @PreAuthorize("hasAuthority('trade:reversal:manage')")
    public ApiResponse<ReversalDetail> execute(
            @PathVariable long id,
            @Valid @RequestBody ExecuteReversalRequest request,
            Principal principal) {
        return ApiResponse.ok(service.execute(id, request, principal.getName()));
    }
}
