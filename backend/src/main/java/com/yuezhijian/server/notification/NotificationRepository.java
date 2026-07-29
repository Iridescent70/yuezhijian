package com.yuezhijian.server.notification;

import com.yuezhijian.server.common.PageResult;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface NotificationRepository {
    PageResult<Announcement> findAnnouncements(
            Long storeId, String keyword, String status, int page, int size);

    Optional<Announcement> findAnnouncement(long id);

    Announcement createAnnouncement(NewAnnouncement announcement);

    Announcement updateAnnouncement(AnnouncementUpdate update);

    PageResult<NotificationItem> findNotifications(NotificationQuery query);

    Optional<NotificationItem> findNotification(long id, long userId, long storeId, LocalDateTime now);

    NotificationItem markRead(long id, long userId, long storeId, LocalDateTime now);

    int markAllRead(NotificationQuery query);

    long unreadCount(long userId, long storeId, LocalDateTime now);

    void publish(BusinessNotificationDraft notification);

    List<NotificationTemplate> findTemplates(String keyword, String status);

    Optional<NotificationTemplate> findTemplate(long id);

    Optional<NotificationTemplate> findActiveTemplate(String eventCode);

    NotificationTemplate createTemplate(NewNotificationTemplate template);

    NotificationTemplate updateTemplate(NotificationTemplateUpdate update);
}
