package com.yuezhijian.server.iam;

import com.yuezhijian.server.common.ApiResponse;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/roles")
public class RoleController {
    private final AccessCatalogService accessCatalogService;

    public RoleController(AccessCatalogService accessCatalogService) {
        this.accessCatalogService = accessCatalogService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('iam:role:view')")
    public ApiResponse<List<RoleSummary>> list() {
        return ApiResponse.ok(accessCatalogService.roles());
    }
}
