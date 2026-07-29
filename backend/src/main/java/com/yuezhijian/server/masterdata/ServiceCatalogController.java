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
public class ServiceCatalogController {
    private final MasterDataService service;

    public ServiceCatalogController(MasterDataService service) {
        this.service = service;
    }

    @GetMapping("/item-categories")
    @PreAuthorize("hasAuthority('catalog:service:view')")
    public ApiResponse<List<CategoryOption>> categories(
            @RequestParam(defaultValue = "SERVICE") String type) {
        if (!"SERVICE".equalsIgnoreCase(type)) {
            throw new IllegalArgumentException("当前接口仅支持服务项目分类");
        }
        return ApiResponse.ok(service.serviceCategories());
    }

    @GetMapping("/services")
    @PreAuthorize("hasAuthority('catalog:service:view')")
    public ApiResponse<List<ServiceItemSummary>> services(
            @RequestParam(required = false) Long storeId,
            @RequestParam(required = false) String keyword) {
        return ApiResponse.ok(service.services(storeId, keyword));
    }

    @PostMapping("/services")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('catalog:service:manage')")
    public ApiResponse<CreatedResource> createService(
            @Valid @RequestBody CreateServiceItemRequest request,
            Principal principal) {
        return ApiResponse.ok(service.createService(request, principal.getName()));
    }
}
