package com.yuezhijian.server.organization;

import com.yuezhijian.server.common.ApiResponse;
import com.yuezhijian.server.iam.CurrentStoreContext;
import com.yuezhijian.server.iam.StoreSummary;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/stores")
public class StoreController {
    private final CurrentStoreContext currentStoreContext;

    public StoreController(CurrentStoreContext currentStoreContext) {
        this.currentStoreContext = currentStoreContext;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('org:store:view')")
    public ApiResponse<List<StoreSummary>> list(Authentication authentication) {
        return ApiResponse.ok(currentStoreContext.availableStores(authentication));
    }
}
