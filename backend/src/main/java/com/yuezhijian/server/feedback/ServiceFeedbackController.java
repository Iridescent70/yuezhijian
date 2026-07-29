package com.yuezhijian.server.feedback;

import com.yuezhijian.server.common.ApiResponse;
import jakarta.validation.Valid;
import java.security.Principal;
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
@RequestMapping("/api/v1/service-feedback")
public class ServiceFeedbackController {
    private final ServiceFeedbackService service;

    public ServiceFeedbackController(ServiceFeedbackService service) { this.service = service; }

    @GetMapping
    @PreAuthorize("hasAuthority('visit:feedback:view')")
    public ApiResponse<List<FeedbackSummary>> feedback(
            @RequestParam(required = false) Long storeId,
            @RequestParam(required = false) Long handlerId,
            @RequestParam(required = false) Integer score,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword) {
        return ApiResponse.ok(service.feedback(storeId, handlerId, score, status, keyword));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('visit:feedback:view')")
    public ApiResponse<FeedbackDetail> detail(@PathVariable long id) {
        return ApiResponse.ok(service.detail(id));
    }

    @PostMapping("/{id}/handle")
    @PreAuthorize("hasAuthority('visit:feedback:manage')")
    public ApiResponse<FeedbackDetail> handle(
            @PathVariable long id,
            @Valid @RequestBody HandleFeedbackRequest request,
            Principal principal) {
        return ApiResponse.ok(service.handle(id, request, principal.getName()));
    }
}
