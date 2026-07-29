package com.yuezhijian.server.product;

import com.yuezhijian.server.common.ApiResponse;
import com.yuezhijian.server.masterdata.CreatedResource;
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
@RequestMapping("/api/v1/products")
public class ProductController {
    private final ProductService service;

    public ProductController(ProductService service) {
        this.service = service;
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

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('catalog:product:manage')")
    public ApiResponse<ProductDetail> update(
            @PathVariable long id,
            @Valid @RequestBody UpdateProductRequest request,
            Principal principal) {
        return ApiResponse.ok(service.update(id, request, principal.getName()));
    }
}
