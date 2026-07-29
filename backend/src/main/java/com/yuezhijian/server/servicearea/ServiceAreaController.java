package com.yuezhijian.server.servicearea;

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
@RequestMapping("/api/v1/service-areas")
public class ServiceAreaController {
    private final ServiceAreaService service;

    public ServiceAreaController(ServiceAreaService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('home:service-area:view')")
    public ApiResponse<List<ServiceArea>> list(
            @RequestParam(required = false) Long storeId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status) {
        return ApiResponse.ok(service.findAll(storeId, keyword, status));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('home:service-area:view')")
    public ApiResponse<ServiceArea> detail(@PathVariable long id) {
        return ApiResponse.ok(service.detail(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('home:service-area:manage')")
    public ApiResponse<ServiceArea> create(
            @Valid @RequestBody CreateServiceAreaRequest request, Principal principal) {
        return ApiResponse.ok(service.create(request, principal.getName()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('home:service-area:manage')")
    public ApiResponse<ServiceArea> update(
            @PathVariable long id,
            @Valid @RequestBody UpdateServiceAreaRequest request,
            Principal principal) {
        return ApiResponse.ok(service.update(id, request, principal.getName()));
    }
}
