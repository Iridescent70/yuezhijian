package com.yuezhijian.server.masterdata;

import com.yuezhijian.server.common.ApiResponse;
import com.yuezhijian.server.iam.CurrentStoreContext;
import com.yuezhijian.server.job.AsyncJobItem;
import com.yuezhijian.server.job.AsyncJobService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1")
public class ServiceCatalogController {
    private final MasterDataService service;
    private final AsyncJobService jobs;
    private final CurrentStoreContext currentStoreContext;

    public ServiceCatalogController(
            MasterDataService service,
            AsyncJobService jobs,
            CurrentStoreContext currentStoreContext) {
        this.service = service;
        this.jobs = jobs;
        this.currentStoreContext = currentStoreContext;
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

    @GetMapping("/services/{id}")
    @PreAuthorize("hasAuthority('catalog:service:view')")
    public ApiResponse<ServiceItemDetail> service(@PathVariable long id) {
        return ApiResponse.ok(service.service(id));
    }

    @PostMapping("/services")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('catalog:service:manage')")
    public ApiResponse<CreatedResource> createService(
            @Valid @RequestBody CreateServiceItemRequest request,
            Principal principal) {
        return ApiResponse.ok(service.createService(request, principal.getName()));
    }

    @PostMapping(value = "/services/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PreAuthorize("hasAuthority('catalog:service:manage') and hasAuthority('system:job:create')")
    public ApiResponse<AsyncJobItem> importServices(
            @RequestParam("file") MultipartFile file,
            Authentication authentication,
            HttpSession session) {
        long storeId = currentStoreContext.currentStore(authentication, session).id();
        return ApiResponse.ok(jobs.createServiceImport(file, authentication.getName(), storeId));
    }

    @PutMapping("/services/{id}")
    @PreAuthorize("hasAuthority('catalog:service:manage')")
    public ApiResponse<ServiceItemDetail> updateService(
            @PathVariable long id,
            @Valid @RequestBody UpdateServiceItemRequest request,
            Principal principal) {
        return ApiResponse.ok(service.updateService(id, request, principal.getName()));
    }
}
