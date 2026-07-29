package com.yuezhijian.server.job;

import com.yuezhijian.server.common.ApiResponse;
import com.yuezhijian.server.common.PageResult;
import com.yuezhijian.server.file.StoredFileDownload;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class AsyncJobController {
    private final AsyncJobService service;

    public AsyncJobController(AsyncJobService service) {
        this.service = service;
    }

    @PostMapping("/exports")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PreAuthorize("hasAuthority('system:job:create')")
    public ApiResponse<AsyncJobItem> createExport(
            @Valid @RequestBody CreateExportRequest request, Principal principal) {
        return ApiResponse.ok(service.createExport(request, principal.getName()));
    }

    @GetMapping("/jobs")
    @PreAuthorize("hasAuthority('system:job:view')")
    public ApiResponse<PageResult<AsyncJobItem>> jobs(
            @RequestParam(required = false) String jobType,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            Principal principal) {
        return ApiResponse.ok(service.jobs(jobType, status, page, size, principal.getName()));
    }

    @GetMapping("/jobs/{id}")
    @PreAuthorize("hasAuthority('system:job:view')")
    public ApiResponse<AsyncJobItem> detail(@PathVariable long id, Principal principal) {
        return ApiResponse.ok(service.detail(id, principal.getName()));
    }

    @PostMapping("/jobs/{id}/cancel")
    @PreAuthorize("hasAuthority('system:job:cancel')")
    public ApiResponse<AsyncJobItem> cancel(@PathVariable long id, Principal principal) {
        return ApiResponse.ok(service.cancel(id, principal.getName()));
    }

    @GetMapping("/jobs/{id}/result")
    @PreAuthorize("hasAuthority('system:job:view')")
    public ResponseEntity<byte[]> downloadResult(@PathVariable long id, Principal principal) {
        StoredFileDownload download = service.downloadResult(id, principal.getName());
        byte[] content = download.content();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(download.file().contentType()))
                .contentLength(content.length)
                .cacheControl(CacheControl.noStore())
                .eTag(download.file().sha256())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(download.file().originalName(), StandardCharsets.UTF_8).build().toString())
                .body(content);
    }
}
