package com.yuezhijian.server.settings;

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
public class SystemSettingsController {
    private final SystemSettingsService service;

    public SystemSettingsController(SystemSettingsService service) { this.service = service; }

    @GetMapping("/system-parameters")
    @PreAuthorize("hasAuthority('system:parameter:view')")
    public ApiResponse<List<SystemParameterItem>> parameters(
            @RequestParam(required = false) String group) {
        return ApiResponse.ok(service.parameters(group));
    }

    @PutMapping("/system-parameters/{id}")
    @PreAuthorize("hasAuthority('system:parameter:manage')")
    public ApiResponse<SystemParameterItem> updateParameter(
            @PathVariable long id,
            @Valid @RequestBody UpdateSystemParameterRequest request,
            Principal principal) {
        return ApiResponse.ok(service.updateParameter(id, request, principal.getName()));
    }

    @GetMapping("/satisfaction-rules")
    @PreAuthorize("hasAuthority('visit:satisfaction:view')")
    public ApiResponse<List<SatisfactionRule>> satisfactionRules(
            @RequestParam(required = false) String status) {
        return ApiResponse.ok(service.satisfactionRules(status));
    }

    @PostMapping("/satisfaction-rules")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('visit:satisfaction:manage')")
    public ApiResponse<SatisfactionRule> createSatisfactionRule(
            @Valid @RequestBody CreateSatisfactionRuleRequest request,
            Principal principal) {
        return ApiResponse.ok(service.createSatisfactionRule(request, principal.getName()));
    }

    @PutMapping("/satisfaction-rules/{id}")
    @PreAuthorize("hasAuthority('visit:satisfaction:manage')")
    public ApiResponse<SatisfactionRule> updateSatisfactionRule(
            @PathVariable long id,
            @Valid @RequestBody UpdateSatisfactionRuleRequest request,
            Principal principal) {
        return ApiResponse.ok(service.updateSatisfactionRule(id, request, principal.getName()));
    }

    @PostMapping("/satisfaction-rules/test")
    @PreAuthorize("hasAuthority('visit:satisfaction:view')")
    public ApiResponse<SatisfactionRuleTestResult> testSatisfactionRule(
            @Valid @RequestBody TestSatisfactionRuleRequest request) {
        return ApiResponse.ok(service.testSatisfactionRule(request.text()));
    }
}
