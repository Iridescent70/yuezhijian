package com.yuezhijian.server.product;

import com.yuezhijian.server.common.ApiResponse;
import com.yuezhijian.server.iam.CurrentStoreContext;
import com.yuezhijian.server.job.AsyncJobItem;
import com.yuezhijian.server.job.AsyncJobService;
import com.yuezhijian.server.masterdata.CreatedResource;
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
@RequestMapping("/api/v1/products")
public class ProductController {
    private final ProductService service;
    private final AsyncJobService jobs;
    private final CurrentStoreContext currentStoreContext;

    public ProductController(
            ProductService service, AsyncJobService jobs, CurrentStoreContext currentStoreContext) {
        this.service = service;
        this.jobs = jobs;
        this.currentStoreContext = currentStoreContext;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('catalog:product:view')")
    public ApiResponse<List<ProductSummary>> products(
            @RequestParam(required = false) Long storeId,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String saleStatus,
            @RequestParam(required = false) String keyword) {
        return ApiResponse.ok(service.products(storeId, categoryId, saleStatus, keyword));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('catalog:product:view')")
    public ApiResponse<ProductDetail> product(@PathVariable long id) {
        return ApiResponse.ok(service.product(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('catalog:product:manage')")
    public ApiResponse<CreatedResource> create(
            @Valid @RequestBody CreateProductRequest request, Principal principal) {
        return ApiResponse.ok(new CreatedResource(service.create(request, principal.getName())));
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PreAuthorize("hasAuthority('catalog:product:manage') and hasAuthority('system:job:create')")
    public ApiResponse<AsyncJobItem> importProducts(
            @RequestParam("file") MultipartFile file,
            Authentication authentication,
            HttpSession session) {
        long storeId = currentStoreContext.currentStore(authentication, session).id();
        return ApiResponse.ok(jobs.createProductImport(file, authentication.getName(), storeId));
    }

    @PostMapping("/batch-status")
    @PreAuthorize("hasAuthority('catalog:product:manage')")
    public ApiResponse<ProductBatchResult> batchStatus(
            @Valid @RequestBody BatchProductSaleStatusRequest request,
            Authentication authentication,
            HttpSession session) {
        long storeId = currentStoreContext.currentStore(authentication, session).id();
        return ApiResponse.ok(service.batchSaleStatus(request, storeId, authentication.getName()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('catalog:product:manage')")
    public ApiResponse<ProductDetail> update(
            @PathVariable long id,
            @Valid @RequestBody UpdateProductRequest request,
            Principal principal) {
        return ApiResponse.ok(service.update(id, request, principal.getName()));
    }
}
