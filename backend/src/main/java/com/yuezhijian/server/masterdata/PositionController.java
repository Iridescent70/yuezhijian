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
@RequestMapping("/api/v1/positions")
public class PositionController {
    private final MasterDataService service;

    public PositionController(MasterDataService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('org:position:view','org:employee:view','commission:plan:view')")
    public ApiResponse<List<PositionOption>> list(
            @RequestParam(defaultValue = "true") boolean activeOnly) {
        return ApiResponse.ok(service.positions(activeOnly));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('org:position:view')")
    public ApiResponse<PositionOption> detail(@PathVariable long id) {
        return ApiResponse.ok(service.position(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('org:position:manage')")
    public ApiResponse<CreatedResource> create(
            @Valid @RequestBody CreatePositionRequest request, Principal principal) {
        return ApiResponse.ok(service.createPosition(request, principal.getName()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('org:position:manage')")
    public ApiResponse<PositionOption> update(
            @PathVariable long id,
            @Valid @RequestBody UpdatePositionRequest request,
            Principal principal) {
        return ApiResponse.ok(service.updatePosition(id, request, principal.getName()));
    }
}
