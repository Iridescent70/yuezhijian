package com.yuezhijian.server.masterdata;

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
public class CatalogMasterDataController {
    private final MasterDataService service;

    public CatalogMasterDataController(MasterDataService service) {
        this.service = service;
    }

    @GetMapping("/item-categories")
    @PreAuthorize("hasAuthority('catalog:master:view') or "
            + "(#activeOnly and hasAnyAuthority('catalog:service:view','catalog:product:view'))")
    public ApiResponse<List<CategoryOption>> categories(
            @RequestParam(defaultValue = "SERVICE") String type,
            @RequestParam(defaultValue = "true") boolean activeOnly) {
        return ApiResponse.ok(service.itemCategories(type, activeOnly));
    }

    @GetMapping("/item-categories/{id}")
    @PreAuthorize("hasAuthority('catalog:master:view')")
    public ApiResponse<CategoryOption> category(@PathVariable long id) {
        return ApiResponse.ok(service.itemCategory(id));
    }

    @PostMapping("/item-categories")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('catalog:master:manage')")
    public ApiResponse<CreatedResource> createCategory(
            @Valid @RequestBody CreateCategoryRequest request, Principal principal) {
        return ApiResponse.ok(service.createCategory(request, principal.getName()));
    }

    @PutMapping("/item-categories/{id}")
    @PreAuthorize("hasAuthority('catalog:master:manage')")
    public ApiResponse<CategoryOption> updateCategory(
            @PathVariable long id,
            @Valid @RequestBody UpdateCategoryRequest request,
            Principal principal) {
        return ApiResponse.ok(service.updateCategory(id, request, principal.getName()));
    }

    @GetMapping("/units")
    @PreAuthorize("hasAuthority('catalog:master:view') or "
            + "(#activeOnly and hasAnyAuthority('catalog:service:view','catalog:product:view'))")
    public ApiResponse<List<UnitOption>> units(
            @RequestParam(defaultValue = "true") boolean activeOnly) {
        return ApiResponse.ok(service.units(activeOnly));
    }

    @GetMapping("/units/{id}")
    @PreAuthorize("hasAuthority('catalog:master:view')")
    public ApiResponse<UnitOption> unit(@PathVariable long id) {
        return ApiResponse.ok(service.unit(id));
    }

    @PostMapping("/units")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('catalog:master:manage')")
    public ApiResponse<CreatedResource> createUnit(
            @Valid @RequestBody CreateUnitRequest request, Principal principal) {
        return ApiResponse.ok(service.createUnit(request, principal.getName()));
    }

    @PutMapping("/units/{id}")
    @PreAuthorize("hasAuthority('catalog:master:manage')")
    public ApiResponse<UnitOption> updateUnit(
            @PathVariable long id,
            @Valid @RequestBody UpdateUnitRequest request,
            Principal principal) {
        return ApiResponse.ok(service.updateUnit(id, request, principal.getName()));
    }
}
