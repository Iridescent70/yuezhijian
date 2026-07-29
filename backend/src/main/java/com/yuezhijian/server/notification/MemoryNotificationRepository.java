package com.yuezhijian.server.notification;

import com.yuezhijian.server.common.DuplicateResourceException;
import com.yuezhijian.server.common.PageResult;
import com.yuezhijian.server.common.ResourceNotFoundException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("memory")
public class MemoryNotificationRepository implements NotificationRepository {
    private final AtomicLong messageIds = new AtomicLong();
    private final AtomicLong templateIds = new AtomicLong();
    private final List<MessageEntry> messages = new ArrayList<>();
    private final List<NotificationTemplate> templates = new ArrayList<>();
    private final Map<ReadKey, LocalDateTime> reads = new HashMap<>();

    public MemoryNotificationRepository() {
        seedTemplate("BILL_REVERSAL", "账单冲销通知", "账单{{billNo}}已冲销",
                "{{storeName}}账单{{billNo}}已完成冲销，冲销单{{reversalNo}}，退款{{refundAmount}}元。原因：{{reason}}",
                List.of("billNo", "reversalNo", "storeName", "refundAmount", "reason"));
        seedTemplate("BALANCE_REVERSAL", "储值冲销通知", "储值{{rechargeNo}}已冲销",
                "会员{{memberName}}的储值{{rechargeNo}}已冲销，金额{{amount}}元。原因：{{reason}}",
                List.of("rechargeNo", "memberName", "amount", "storeName", "reason"));
        seedTemplate("CARD_REVERSAL", "次卡冲销通知", "次卡{{cardNo}}已冲销",
                "会员{{memberName}}的次卡{{cardNo}}已冲销{{quantity}}次。原因：{{reason}}",
                List.of("cardNo", "memberName", "itemName", "quantity", "storeName", "reason"));
    }

    @Override
    public synchronized PageResult<Announcement> findAnnouncements(
            Long storeId, String keyword, String status, int page, int size) {
        String normalized = keyword == null ? null : keyword.toLowerCase(Locale.ROOT);
        List<Announcement> matched = messages.stream()
                .filter(entry -> "ANNOUNCEMENT".equals(entry.messageType))
                .filter(entry -> storeId == null || entry.visibleAt(storeId))
                .filter(entry -> status == null || status.equals(entry.status))
                .filter(entry -> normalized == null
                        || entry.title.toLowerCase(Locale.ROOT).contains(normalized)
                        || entry.body.toLowerCase(Locale.ROOT).contains(normalized))
                .sorted(Comparator.comparing((MessageEntry entry) -> entry.updatedAt).reversed()
                        .thenComparing(entry -> entry.id, Comparator.reverseOrder()))
                .map(MessageEntry::announcement)
                .toList();
        int from = Math.min((page - 1) * size, matched.size());
        int to = Math.min(from + size, matched.size());
        return new PageResult<>(matched.subList(from, to), page, size, matched.size());
    }

    @Override
    public synchronized Optional<Announcement> findAnnouncement(long id) {
        return messages.stream().filter(entry -> entry.id == id && "ANNOUNCEMENT".equals(entry.messageType))
                .findFirst().map(MessageEntry::announcement);
    }

    @Override
    public synchronized Announcement createAnnouncement(NewAnnouncement draft) {
        if (messages.stream().anyMatch(entry -> entry.notificationNo.equals(draft.notificationNo()))) {
            throw new DuplicateResourceException("通知编号已存在");
        }
        MessageEntry entry = MessageEntry.announcement(messageIds.incrementAndGet(), draft);
        messages.add(entry);
        return entry.announcement();
    }

    @Override
    public synchronized Announcement updateAnnouncement(AnnouncementUpdate update) {
        MessageEntry entry = requireMessage(update.id(), "ANNOUNCEMENT");
        requireVersion(entry, update.version());
        entry.title = update.title();
        entry.body = update.body();
        entry.scopeType = update.scopeType();
        entry.storeIds = new LinkedHashSet<>(update.storeIds());
        entry.validFrom = update.validFrom();
        entry.validTo = update.validTo();
        entry.priority = update.priority();
        entry.pinned = update.pinned();
        entry.status = update.status();
        if ("PUBLISHED".equals(update.status()) && entry.publishedAt == null) entry.publishedAt = LocalDateTime.now();
        entry.updatedAt = LocalDateTime.now();
        entry.updatedBy = update.operatorId();
        entry.version++;
        return entry.announcement();
    }

    @Override
    public synchronized PageResult<NotificationItem> findNotifications(NotificationQuery query) {
        List<NotificationItem> matched = visible(query.userId(), query.storeId(), query.now()).stream()
                .filter(entry -> query.messageType() == null || query.messageType().equals(entry.messageType))
                .filter(entry -> query.publishedFrom() == null || !entry.publishedAt.isBefore(query.publishedFrom()))
                .filter(entry -> query.publishedTo() == null || entry.publishedAt.isBefore(query.publishedTo()))
                .filter(entry -> query.readStatus() == null
                        || ("READ".equals(query.readStatus()) == reads.containsKey(new ReadKey(entry.id, query.userId()))))
                .sorted(messageOrder())
                .map(entry -> item(entry, query.userId()))
                .toList();
        int from = Math.min(query.offset(), matched.size());
        int to = Math.min(from + query.size(), matched.size());
        return new PageResult<>(matched.subList(from, to), query.page(), query.size(), matched.size());
    }

    @Override
    public synchronized Optional<NotificationItem> findNotification(
            long id, long userId, long storeId, LocalDateTime now) {
        return visible(userId, storeId, now).stream().filter(entry -> entry.id == id).findFirst()
                .map(entry -> item(entry, userId));
    }

    @Override
    public synchronized NotificationItem markRead(long id, long userId, long storeId, LocalDateTime now) {
        MessageEntry entry = visible(userId, storeId, now).stream().filter(value -> value.id == id).findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("通知不存在"));
        reads.putIfAbsent(new ReadKey(id, userId), LocalDateTime.now());
        return item(entry, userId);
    }

    @Override
    public synchronized int markAllRead(NotificationQuery query) {
        int changed = 0;
        for (MessageEntry entry : visible(query.userId(), query.storeId(), query.now())) {
            if (query.messageType() != null && !query.messageType().equals(entry.messageType)) continue;
            ReadKey key = new ReadKey(entry.id, query.userId());
            if (!reads.containsKey(key)) {
                reads.put(key, LocalDateTime.now());
                changed++;
            }
        }
        return changed;
    }

    @Override
    public synchronized long unreadCount(long userId, long storeId, LocalDateTime now) {
        return visible(userId, storeId, now).stream()
                .filter(entry -> !reads.containsKey(new ReadKey(entry.id, userId))).count();
    }

    @Override
    public synchronized void publish(BusinessNotificationDraft draft) {
        boolean exists = messages.stream().anyMatch(entry -> draft.eventCode().equals(entry.eventCode)
                && draft.businessType().equals(entry.businessType)
                && Long.valueOf(draft.businessId()).equals(entry.businessId));
        if (exists) return;
        messages.add(MessageEntry.business(messageIds.incrementAndGet(), draft));
    }

    @Override
    public synchronized List<NotificationTemplate> findTemplates(String keyword, String status) {
        String normalized = keyword == null ? null : keyword.toLowerCase(Locale.ROOT);
        return templates.stream()
                .filter(template -> status == null || status.equals(template.status()))
                .filter(template -> normalized == null
                        || template.eventCode().toLowerCase(Locale.ROOT).contains(normalized)
                        || template.eventName().toLowerCase(Locale.ROOT).contains(normalized))
                .sorted(Comparator.comparing(NotificationTemplate::eventCode))
                .toList();
    }

    @Override
    public synchronized Optional<NotificationTemplate> findTemplate(long id) {
        return templates.stream().filter(template -> template.id() == id).findFirst();
    }

    @Override
    public synchronized Optional<NotificationTemplate> findActiveTemplate(String eventCode) {
        return templates.stream().filter(template -> eventCode.equals(template.eventCode()))
                .filter(template -> "ACTIVE".equals(template.status())).findFirst();
    }

    @Override
    public synchronized NotificationTemplate createTemplate(NewNotificationTemplate draft) {
        if (templates.stream().anyMatch(template -> template.eventCode().equals(draft.eventCode()))) {
            throw new DuplicateResourceException("事件编码已存在");
        }
        NotificationTemplate created = toTemplate(
                templateIds.incrementAndGet(), draft.eventCode(), draft.eventName(), draft.titleTemplate(),
                draft.bodyTemplate(), draft.variablesCsv(), draft.status(), draft.operatorId(), 1);
        templates.add(created);
        return created;
    }

    @Override
    public synchronized NotificationTemplate updateTemplate(NotificationTemplateUpdate update) {
        NotificationTemplate current = findTemplate(update.id())
                .orElseThrow(() -> new ResourceNotFoundException("通知模板不存在"));
        if (!current.version().equals(update.version())) {
            throw new DuplicateResourceException("通知模板已被他人修改，请刷新后重试");
        }
        NotificationTemplate saved = toTemplate(
                current.id(), current.eventCode(), update.eventName(), update.titleTemplate(),
                update.bodyTemplate(), update.variablesCsv(), update.status(), update.operatorId(),
                Long.parseLong(current.version()) + 1);
        templates.set(templates.indexOf(current), saved);
        return saved;
    }

    private List<MessageEntry> visible(long userId, long storeId, LocalDateTime now) {
        return messages.stream()
                .filter(entry -> "PUBLISHED".equals(entry.status))
                .filter(entry -> entry.publishedAt != null && !entry.publishedAt.isAfter(now))
                .filter(entry -> entry.validFrom == null || !entry.validFrom.isAfter(now))
                .filter(entry -> entry.validTo == null || !entry.validTo.isBefore(now))
                .filter(entry -> entry.visibleAt(storeId))
                .toList();
    }

    private NotificationItem item(MessageEntry entry, long userId) {
        LocalDateTime readAt = reads.get(new ReadKey(entry.id, userId));
        return new NotificationItem(
                entry.id, entry.notificationNo, entry.messageType, entry.eventCode, entry.title, entry.body,
                entry.businessType, entry.businessId, entry.route, entry.priority, entry.pinned,
                entry.publishedAt, entry.validTo, readAt != null, readAt);
    }

    private MessageEntry requireMessage(long id, String type) {
        return messages.stream().filter(entry -> entry.id == id && type.equals(entry.messageType)).findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("通知公告不存在"));
    }

    private static void requireVersion(MessageEntry entry, String version) {
        if (!String.valueOf(entry.version).equals(version)) {
            throw new DuplicateResourceException("通知公告已被他人修改，请刷新后重试");
        }
    }

    private void seedTemplate(
            String eventCode, String eventName, String title, String body, List<String> variables) {
        templates.add(new NotificationTemplate(
                templateIds.incrementAndGet(), eventCode, eventName, "IN_APP", title, body, variables,
                "ACTIVE", LocalDateTime.now(), null, "系统初始化", "1"));
    }

    private static NotificationTemplate toTemplate(
            long id, String code, String name, String title, String body, String csv,
            String status, long operatorId, long version) {
        List<String> variables = csv.isBlank() ? List.of() : List.of(csv.split(","));
        return new NotificationTemplate(
                id, code, name, "IN_APP", title, body, variables, status, LocalDateTime.now(),
                operatorId, operatorId == 1 ? "本地管理员" : "用户" + operatorId, String.valueOf(version));
    }

    private static Comparator<MessageEntry> messageOrder() {
        return Comparator.comparing((MessageEntry entry) -> entry.pinned).reversed()
                .thenComparing(Comparator.comparingInt((MessageEntry entry) -> entry.priority).reversed())
                .thenComparing((MessageEntry entry) -> entry.publishedAt, Comparator.reverseOrder())
                .thenComparing(entry -> entry.id, Comparator.reverseOrder());
    }

    private record ReadKey(long messageId, long userId) {
    }

    private static final class MessageEntry {
        private final long id;
        private final String notificationNo;
        private final String messageType;
        private final String eventCode;
        private String title;
        private String body;
        private String scopeType;
        private Set<Long> storeIds;
        private final String businessType;
        private final Long businessId;
        private final String route;
        private LocalDateTime validFrom;
        private LocalDateTime validTo;
        private int priority;
        private boolean pinned;
        private String status;
        private LocalDateTime publishedAt;
        private final LocalDateTime createdAt;
        private LocalDateTime updatedAt;
        private long updatedBy;
        private long version;

        private MessageEntry(
                long id, String no, String type, String eventCode, String title, String body,
                String scopeType, Set<Long> storeIds, String businessType, Long businessId, String route,
                LocalDateTime validFrom, LocalDateTime validTo, int priority, boolean pinned,
                String status, LocalDateTime publishedAt, long operatorId) {
            this.id = id;
            this.notificationNo = no;
            this.messageType = type;
            this.eventCode = eventCode;
            this.title = title;
            this.body = body;
            this.scopeType = scopeType;
            this.storeIds = storeIds;
            this.businessType = businessType;
            this.businessId = businessId;
            this.route = route;
            this.validFrom = validFrom;
            this.validTo = validTo;
            this.priority = priority;
            this.pinned = pinned;
            this.status = status;
            this.publishedAt = publishedAt;
            this.createdAt = LocalDateTime.now();
            this.updatedAt = createdAt;
            this.updatedBy = operatorId;
            this.version = 1;
        }

        private static MessageEntry announcement(long id, NewAnnouncement draft) {
            LocalDateTime publishedAt = "PUBLISHED".equals(draft.status()) ? LocalDateTime.now() : null;
            return new MessageEntry(
                    id, draft.notificationNo(), "ANNOUNCEMENT", "ANNOUNCEMENT", draft.title(), draft.body(),
                    draft.scopeType(), new LinkedHashSet<>(draft.storeIds()), null, null, null,
                    draft.validFrom(), draft.validTo(), draft.priority(), draft.pinned(), draft.status(),
                    publishedAt, draft.operatorId());
        }

        private static MessageEntry business(long id, BusinessNotificationDraft draft) {
            return new MessageEntry(
                    id, draft.notificationNo(), draft.messageType(), draft.eventCode(), draft.title(), draft.body(),
                    "STORES", new LinkedHashSet<>(List.of(draft.storeId())), draft.businessType(),
                    draft.businessId(), draft.route(), null, null, draft.priority(), false, "PUBLISHED",
                    LocalDateTime.now(), draft.operatorId());
        }

        private boolean visibleAt(long storeId) {
            return "ALL".equals(scopeType) || storeIds.contains(storeId);
        }

        private Announcement announcement() {
            return new Announcement(
                    id, notificationNo, title, body, scopeType, List.copyOf(storeIds), validFrom, validTo,
                    priority, pinned, status, publishedAt, createdAt, updatedAt, updatedBy,
                    updatedBy == 1 ? "本地管理员" : "用户" + updatedBy, String.valueOf(version));
        }
    }
}
