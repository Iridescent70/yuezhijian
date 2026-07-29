package com.yuezhijian.server.notification;

import com.yuezhijian.server.common.ApiResponse;
import com.yuezhijian.server.common.PageResult;
import jakarta.validation.Valid;
import java.security.Principal;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
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
public class NotificationController {
    private final NotificationService service;

    public NotificationController(NotificationService service) {
        this.service = service;
    }

    @GetMapping("/notifications")
    @PreAuthorize("hasAuthority('notification:view')")
    public ApiResponse<PageResult<NotificationItem>> notifications(
            @RequestParam(required = false) String messageType,
            @RequestParam(required = false) String readStatus,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate publishedFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate publishedTo,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            Principal principal) {
        return ApiResponse.ok(service.notifications(
                messageType, readStatus, publishedFrom, publishedTo, page, size, principal.getName()));
    }

    @GetMapping("/notifications/unread-count")
    @PreAuthorize("hasAuthority('notification:view')")
    public ApiResponse<UnreadNotificationCount> unreadCount(Principal principal) {
        return ApiResponse.ok(service.unreadCount(principal.getName()));
    }

    @GetMapping("/notifications/{id}")
    @PreAuthorize("hasAuthority('notification:view')")
    public ApiResponse<NotificationItem> notification(@PathVariable long id, Principal principal) {
        return ApiResponse.ok(service.notification(id, principal.getName()));
    }

    @PostMapping("/notifications/{id}/read")
    @PreAuthorize("hasAuthority('notification:view')")
    public ApiResponse<NotificationItem> markRead(@PathVariable long id, Principal principal) {
        return ApiResponse.ok(service.markRead(id, principal.getName()));
    }

    @PostMapping("/notifications/read-all")
    @PreAuthorize("hasAuthority('notification:view')")
    public ApiResponse<ReadAllNotificationsResult> markAllRead(
            @RequestParam(required = false) String messageType, Principal principal) {
        return ApiResponse.ok(service.markAllRead(messageType, principal.getName()));
    }

    @PostMapping("/notifications/test")
    @PreAuthorize("hasAuthority('system:notification-template:manage')")
    public ApiResponse<TestNotificationResult> sendTest(
            @Valid @RequestBody SendTestNotificationRequest request, Principal principal) {
        return ApiResponse.ok(service.sendTest(request, principal.getName()));
    }

    @GetMapping("/announcements")
    @PreAuthorize("hasAuthority('system:announcement:view')")
    public ApiResponse<PageResult<Announcement>> announcements(
            @RequestParam(required = false) Long storeId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(service.announcements(storeId, keyword, status, page, size));
    }

    @GetMapping("/announcements/{id}")
    @PreAuthorize("hasAuthority('system:announcement:view')")
    public ApiResponse<Announcement> announcement(@PathVariable long id) {
        return ApiResponse.ok(service.announcement(id));
    }

    @PostMapping("/announcements")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('system:announcement:manage')")
    public ApiResponse<Announcement> createAnnouncement(
            @Valid @RequestBody CreateAnnouncementRequest request, Principal principal) {
        return ApiResponse.ok(service.createAnnouncement(request, principal.getName()));
    }

    @PutMapping("/announcements/{id}")
    @PreAuthorize("hasAuthority('system:announcement:manage')")
    public ApiResponse<Announcement> updateAnnouncement(
            @PathVariable long id,
            @Valid @RequestBody UpdateAnnouncementRequest request,
            Principal principal) {
        return ApiResponse.ok(service.updateAnnouncement(id, request, principal.getName()));
    }

    @GetMapping("/notification-templates")
    @PreAuthorize("hasAuthority('system:notification-template:view')")
    public ApiResponse<List<NotificationTemplate>> templates(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status) {
        return ApiResponse.ok(service.templates(keyword, status));
    }

    @GetMapping("/notification-templates/{id}")
    @PreAuthorize("hasAuthority('system:notification-template:view')")
    public ApiResponse<NotificationTemplate> template(@PathVariable long id) {
        return ApiResponse.ok(service.template(id));
    }

    @PostMapping("/notification-templates")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('system:notification-template:manage')")
    public ApiResponse<NotificationTemplate> createTemplate(
            @Valid @RequestBody CreateNotificationTemplateRequest request, Principal principal) {
        return ApiResponse.ok(service.createTemplate(request, principal.getName()));
    }

    @PutMapping("/notification-templates/{id}")
    @PreAuthorize("hasAuthority('system:notification-template:manage')")
    public ApiResponse<NotificationTemplate> updateTemplate(
            @PathVariable long id,
            @Valid @RequestBody UpdateNotificationTemplateRequest request,
            Principal principal) {
        return ApiResponse.ok(service.updateTemplate(id, request, principal.getName()));
    }
}
