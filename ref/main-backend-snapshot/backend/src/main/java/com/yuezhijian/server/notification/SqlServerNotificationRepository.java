package com.yuezhijian.server.notification;

import com.yuezhijian.server.common.DuplicateResourceException;
import com.yuezhijian.server.common.PageResult;
import com.yuezhijian.server.common.ResourceNotFoundException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Profile("sqlserver")
public class SqlServerNotificationRepository implements NotificationRepository {
    private final NotificationMapper mapper;

    public SqlServerNotificationRepository(NotificationMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public PageResult<Announcement> findAnnouncements(
            Long storeId, String keyword, String status, int page, int size) {
        int offset = (page - 1) * size;
        List<Announcement> items = mapper.findAnnouncements(storeId, keyword, status, offset, size).stream()
                .map(this::announcement).toList();
        return new PageResult<>(items, page, size, mapper.countAnnouncements(storeId, keyword, status));
    }

    @Override
    public Optional<Announcement> findAnnouncement(long id) {
        AnnouncementRow row = mapper.findAnnouncement(id);
        return row == null ? Optional.empty() : Optional.of(announcement(row));
    }

    @Override
    @Transactional
    public Announcement createAnnouncement(NewAnnouncement announcement) {
        try {
            long id = mapper.insertAnnouncement(announcement);
            replaceStores(id, announcement.storeIds(), announcement.operatorId());
            return findAnnouncement(id).orElseThrow();
        } catch (DataIntegrityViolationException exception) {
            throw new DuplicateResourceException("通知公告与现有数据冲突");
        }
    }

    @Override
    @Transactional
    public Announcement updateAnnouncement(AnnouncementUpdate update) {
        if (mapper.updateAnnouncement(update) == 0) {
            if (mapper.findAnnouncement(update.id()) == null) {
                throw new ResourceNotFoundException("通知公告不存在");
            }
            throw new DuplicateResourceException("通知公告已被他人修改，请刷新后重试");
        }
        replaceStores(update.id(), update.storeIds(), update.operatorId());
        return findAnnouncement(update.id()).orElseThrow();
    }

    @Override
    public PageResult<NotificationItem> findNotifications(NotificationQuery query) {
        return new PageResult<>(
                mapper.findNotifications(query), query.page(), query.size(), mapper.countNotifications(query));
    }

    @Override
    public Optional<NotificationItem> findNotification(
            long id, long userId, long storeId, LocalDateTime now) {
        return Optional.ofNullable(mapper.findNotification(id, userId, storeId, now));
    }

    @Override
    @Transactional
    public NotificationItem markRead(long id, long userId, long storeId, LocalDateTime now) {
        if (mapper.findNotification(id, userId, storeId, now) == null) {
            throw new ResourceNotFoundException("通知不存在");
        }
        mapper.markRead(id, userId, LocalDateTime.now());
        return Optional.ofNullable(mapper.findNotification(id, userId, storeId, now)).orElseThrow();
    }

    @Override
    @Transactional
    public int markAllRead(NotificationQuery query) {
        return mapper.markAllRead(query);
    }

    @Override
    public long unreadCount(long userId, long storeId, LocalDateTime now) {
        return mapper.unreadCount(userId, storeId, now);
    }

    @Override
    @Transactional
    public void publish(BusinessNotificationDraft notification) {
        if (mapper.findBusinessMessageId(
                notification.eventCode(), notification.businessType(), notification.businessId()) != null) return;
        try {
            long id = mapper.insertBusinessNotification(notification);
            mapper.insertStores(id, List.of(notification.storeId()), notification.operatorId());
        } catch (DataIntegrityViolationException exception) {
            if (mapper.findBusinessMessageId(
                    notification.eventCode(), notification.businessType(), notification.businessId()) == null) {
                throw exception;
            }
        }
    }

    @Override
    public List<NotificationTemplate> findTemplates(String keyword, String status) {
        return mapper.findTemplates(keyword, status).stream().map(this::template).toList();
    }

    @Override
    public Optional<NotificationTemplate> findTemplate(long id) {
        return Optional.ofNullable(mapper.findTemplate(id)).map(this::template);
    }

    @Override
    public Optional<NotificationTemplate> findActiveTemplate(String eventCode) {
        return Optional.ofNullable(mapper.findActiveTemplate(eventCode)).map(this::template);
    }

    @Override
    public NotificationTemplate createTemplate(NewNotificationTemplate template) {
        try {
            return findTemplate(mapper.insertTemplate(template)).orElseThrow();
        } catch (DataIntegrityViolationException exception) {
            throw new DuplicateResourceException("事件编码已存在");
        }
    }

    @Override
    public NotificationTemplate updateTemplate(NotificationTemplateUpdate update) {
        if (mapper.updateTemplate(update) == 0) {
            if (mapper.findTemplate(update.id()) == null) throw new ResourceNotFoundException("通知模板不存在");
            throw new DuplicateResourceException("通知模板已被他人修改，请刷新后重试");
        }
        return findTemplate(update.id()).orElseThrow();
    }

    private void replaceStores(long messageId, List<Long> storeIds, long operatorId) {
        mapper.deleteStores(messageId);
        if (!storeIds.isEmpty()) mapper.insertStores(messageId, storeIds, operatorId);
    }

    private Announcement announcement(AnnouncementRow row) {
        return new Announcement(
                row.id(), row.notificationNo(), row.title(), row.body(), row.scopeType(),
                mapper.findStoreIds(row.id()), row.validFrom(), row.validTo(), row.priority(), row.pinned(),
                row.status(), row.publishedAt(), row.createdAt(), row.updatedAt(), row.updatedBy(),
                row.updatedByName(), row.version());
    }

    private NotificationTemplate template(NotificationTemplateRow row) {
        List<String> variables = row.variablesCsv() == null || row.variablesCsv().isBlank()
                ? List.of() : List.of(row.variablesCsv().split(","));
        return new NotificationTemplate(
                row.id(), row.eventCode(), row.eventName(), row.channel(), row.titleTemplate(),
                row.bodyTemplate(), variables, row.status(), row.updatedAt(), row.updatedBy(),
                row.updatedByName(), row.version());
    }
}
