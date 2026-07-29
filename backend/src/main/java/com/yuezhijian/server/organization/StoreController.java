package com.yuezhijian.server.organization;

import com.yuezhijian.server.common.ApiResponse;
import com.yuezhijian.server.iam.AccessCatalogService;
import com.yuezhijian.server.iam.StoreSummary;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/stores")
public class StoreController {
    private final AccessCatalogService accessCatalogService;

    public StoreController(AccessCatalogService accessCatalogService) {
        this.accessCatalogService = accessCatalogService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('org:store:view')")
    public ApiResponse<List<StoreSummary>> list() {
        return ApiResponse.ok(accessCatalogService.stores());
    }
}
