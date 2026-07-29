package com.yuezhijian.server.banner;

import com.yuezhijian.server.common.ApiResponse;
import com.yuezhijian.server.file.StoredFileDownload;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/banners")
public class BannerController {
    private final BannerService service;

    public BannerController(BannerService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('system:banner:view')")
    public ApiResponse<List<Banner>> list(
            @RequestParam(required = false) String positionCode,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status) {
        return ApiResponse.ok(service.findAll(positionCode, keyword, status));
    }

    @GetMapping("/active")
    @PreAuthorize("hasAnyAuthority('workbench:view','system:banner:view')")
    public ApiResponse<List<ActiveBanner>> active(
            @RequestParam(defaultValue = "PC_HOME") String positionCode) {
        return ApiResponse.ok(service.active(positionCode));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('system:banner:view')")
    public ApiResponse<Banner> detail(@PathVariable long id) {
        return ApiResponse.ok(service.detail(id));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('system:banner:manage')")
    public ApiResponse<Banner> create(
            @Valid @RequestPart("request") CreateBannerRequest request,
            @RequestPart("file") MultipartFile file,
            Principal principal) {
        return ApiResponse.ok(service.create(request, file, principal.getName()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('system:banner:manage')")
    public ApiResponse<Banner> update(
            @PathVariable long id,
            @Valid @RequestBody UpdateBannerRequest request,
            Principal principal) {
        return ApiResponse.ok(service.update(id, request, principal.getName()));
    }

    @PutMapping(value = "/{id}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('system:banner:manage')")
    public ApiResponse<Banner> replaceImage(
            @PathVariable long id,
            @RequestParam String version,
            @RequestPart("file") MultipartFile file,
            Principal principal) {
        return ApiResponse.ok(service.replaceImage(id, version, file, principal.getName()));
    }

    @GetMapping("/{id}/image")
    @PreAuthorize("hasAuthority('system:banner:view')")
    public ResponseEntity<byte[]> managementImage(@PathVariable long id) {
        return imageResponse(service.image(id, false), true);
    }

    @GetMapping("/active/{id}/image")
    @PreAuthorize("hasAnyAuthority('workbench:view','system:banner:view')")
    public ResponseEntity<byte[]> activeImage(@PathVariable long id) {
        return imageResponse(service.image(id, true), false);
    }

    private ResponseEntity<byte[]> imageResponse(StoredFileDownload download, boolean noStore) {
        byte[] content = download.content();
        CacheControl cache = noStore ? CacheControl.noStore() : CacheControl.noCache().cachePrivate();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(download.file().contentType()))
                .contentLength(content.length)
                .cacheControl(cache)
                .eTag(download.file().sha256())
                .body(content);
    }
}
