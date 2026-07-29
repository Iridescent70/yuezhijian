package com.yuezhijian.server.feedback;

import com.yuezhijian.server.common.ApiResponse;
import com.yuezhijian.server.file.BusinessAttachmentItem;
import com.yuezhijian.server.file.StoredFileDownload;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/service-feedback")
public class ServiceFeedbackController {
    private final ServiceFeedbackService service;

    public ServiceFeedbackController(ServiceFeedbackService service) { this.service = service; }

    @GetMapping
    @PreAuthorize("hasAuthority('visit:feedback:view')")
    public ApiResponse<List<FeedbackSummary>> feedback(
            @RequestParam(required = false) Long storeId,
            @RequestParam(required = false) Long handlerId,
            @RequestParam(required = false) Integer score,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Boolean overdue,
            @RequestParam(required = false) String keyword) {
        return ApiResponse.ok(service.feedback(storeId, handlerId, score, status, overdue, keyword));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('visit:feedback:view')")
    public ApiResponse<FeedbackDetail> detail(@PathVariable long id) {
        return ApiResponse.ok(service.detail(id));
    }

    @PostMapping("/{id}/handle")
    @PreAuthorize("hasAuthority('visit:feedback:manage')")
    public ApiResponse<FeedbackDetail> handle(
            @PathVariable long id,
            @Valid @RequestBody HandleFeedbackRequest request,
            Principal principal) {
        return ApiResponse.ok(service.handle(id, request, principal.getName()));
    }

    @PostMapping(value = "/{id}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('visit:feedback:manage')")
    public ApiResponse<BusinessAttachmentItem> uploadAttachment(
            @PathVariable long id,
            @RequestParam("file") MultipartFile file,
            Principal principal) {
        return ApiResponse.ok(service.uploadAttachment(id, file, principal.getName()));
    }

    @GetMapping("/{id}/attachments/{attachmentId}/content")
    @PreAuthorize("hasAuthority('visit:feedback:view')")
    public ResponseEntity<byte[]> downloadAttachment(
            @PathVariable long id, @PathVariable long attachmentId) {
        StoredFileDownload download = service.downloadAttachment(id, attachmentId);
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

    @DeleteMapping("/{id}/attachments/{attachmentId}")
    @PreAuthorize("hasAuthority('visit:feedback:manage')")
    public ApiResponse<Void> removeAttachment(
            @PathVariable long id, @PathVariable long attachmentId, Principal principal) {
        service.removeAttachment(id, attachmentId, principal.getName());
        return ApiResponse.ok(null);
    }
}
