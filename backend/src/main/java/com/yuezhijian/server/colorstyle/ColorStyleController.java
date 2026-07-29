package com.yuezhijian.server.colorstyle;

import com.yuezhijian.server.common.ApiResponse;
import com.yuezhijian.server.common.PageResult;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1")
public class ColorStyleController {
    private final ColorStyleService service;

    public ColorStyleController(ColorStyleService service) {
        this.service = service;
    }

    @GetMapping("/color-style-categories")
    @PreAuthorize("hasAuthority('system:color-style:view')")
    public ApiResponse<List<ColorStyleCategory>> categories(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status) {
        return ApiResponse.ok(service.categories(keyword, status));
    }

    @GetMapping("/color-style-categories/{id}")
    @PreAuthorize("hasAuthority('system:color-style:view')")
    public ApiResponse<ColorStyleCategory> category(@PathVariable long id) {
        return ApiResponse.ok(service.category(id));
    }

    @PostMapping("/color-style-categories")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('system:color-style:manage')")
    public ApiResponse<ColorStyleCategory> createCategory(
            @Valid @RequestBody CreateColorStyleCategoryRequest request, Principal principal) {
        return ApiResponse.ok(service.createCategory(request, principal.getName()));
    }

    @PutMapping("/color-style-categories/{id}")
    @PreAuthorize("hasAuthority('system:color-style:manage')")
    public ApiResponse<ColorStyleCategory> updateCategory(
            @PathVariable long id,
            @Valid @RequestBody UpdateColorStyleCategoryRequest request,
            Principal principal) {
        return ApiResponse.ok(service.updateCategory(id, request, principal.getName()));
    }

    @PutMapping(value = "/color-style-categories/{id}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('system:color-style:manage')")
    public ApiResponse<ColorStyleCategory> replaceCategoryImage(
            @PathVariable long id,
            @RequestParam String version,
            @RequestPart("file") MultipartFile file,
            Principal principal) {
        return ApiResponse.ok(service.replaceCategoryImage(id, version, file, principal.getName()));
    }

    @GetMapping("/color-style-categories/{id}/image")
    @PreAuthorize("hasAuthority('system:color-style:view')")
    public ResponseEntity<byte[]> categoryImage(@PathVariable long id) {
        return image(service.categoryImage(id));
    }

    @GetMapping("/color-styles")
    @PreAuthorize("hasAuthority('system:color-style:view')")
    public ApiResponse<PageResult<ColorStyle>> styles(
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(service.styles(categoryId, keyword, status, page, size));
    }

    @GetMapping("/color-styles/{id}")
    @PreAuthorize("hasAuthority('system:color-style:view')")
    public ApiResponse<ColorStyle> style(@PathVariable long id) {
        return ApiResponse.ok(service.style(id));
    }

    @PostMapping("/color-styles")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('system:color-style:manage')")
    public ApiResponse<ColorStyle> createStyle(
            @Valid @RequestBody CreateColorStyleRequest request, Principal principal) {
        return ApiResponse.ok(service.createStyle(request, principal.getName()));
    }

    @PutMapping("/color-styles/{id}")
    @PreAuthorize("hasAuthority('system:color-style:manage')")
    public ApiResponse<ColorStyle> updateStyle(
            @PathVariable long id,
            @Valid @RequestBody UpdateColorStyleRequest request,
            Principal principal) {
        return ApiResponse.ok(service.updateStyle(id, request, principal.getName()));
    }

    @PostMapping(value = "/color-styles/{id}/assets", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('system:color-style:manage')")
    public ApiResponse<ColorStyleAsset> addAsset(
            @PathVariable long id,
            @RequestParam(defaultValue = "0") int sortNo,
            @RequestPart("file") MultipartFile file,
            Principal principal) {
        return ApiResponse.ok(service.addAsset(id, sortNo, file, principal.getName()));
    }

    @PutMapping("/color-styles/{styleId}/assets/{assetId}")
    @PreAuthorize("hasAuthority('system:color-style:manage')")
    public ApiResponse<ColorStyleAsset> updateAsset(
            @PathVariable long styleId,
            @PathVariable long assetId,
            @Valid @RequestBody UpdateColorStyleAssetRequest request,
            Principal principal) {
        return ApiResponse.ok(service.updateAsset(
                styleId, assetId, request, principal.getName()));
    }

    @GetMapping("/color-styles/{styleId}/assets/{assetId}/content")
    @PreAuthorize("hasAuthority('system:color-style:view')")
    public ResponseEntity<byte[]> assetContent(
            @PathVariable long styleId, @PathVariable long assetId) {
        return image(service.assetContent(styleId, assetId));
    }

    private ResponseEntity<byte[]> image(StoredFileDownload download) {
        byte[] content = download.content();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(download.file().contentType()))
                .contentLength(content.length)
                .cacheControl(CacheControl.noStore())
                .eTag(download.file().sha256())
                .body(content);
    }
}
