package com.yuezhijian.server.notification;

import com.yuezhijian.server.audit.AuditService;
import com.yuezhijian.server.common.PageResult;
import com.yuezhijian.server.common.ResourceNotFoundException;
import com.yuezhijian.server.iam.AccessCatalogService;
import com.yuezhijian.server.iam.StoreDataScope;
import com.yuezhijian.server.iam.UserIdentity;
import com.yuezhijian.server.trade.BillDetail;
import com.yuezhijian.server.trade.ReversalDetail;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationService {
    private static final Set<String> ANNOUNCEMENT_STATUSES = Set.of("DRAFT", "PUBLISHED", "DISABLED");
    private static final Set<String> TEMPLATE_STATUSES = Set.of("ACTIVE", "DISABLED");
    private static final Set<String> MESSAGE_TYPES = Set.of(
            "ANNOUNCEMENT", "APPOINTMENT", "CARD_EXPIRY", "BIRTHDAY", "BALANCE_LOW",
            "CONSUMPTION", "SYSTEM", "DAILY_REPORT", "BILL_ALERT", "RECONCILIATION",
            "BILL_REVERSAL", "BALANCE_REVERSAL", "CARD_REVERSAL");
    private static final Pattern EVENT_CODE = Pattern.compile("[A-Z][A-Z0-9_]{1,63}");
    private static final Pattern VARIABLE = Pattern.compile("[a-z][A-Za-z0-9]{0,63}");
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{([a-z][A-Za-z0-9]{0,63})}}" );

    private final NotificationRepository repository;
    private final AccessCatalogService accessCatalog;
    private final StoreDataScope storeDataScope;
    private final NotificationNumberGenerator numbers;
    private final AuditService audit;

    public NotificationService(
            NotificationRepository repository,
            AccessCatalogService accessCatalog,
            StoreDataScope storeDataScope,
            NotificationNumberGenerator numbers,
            AuditService audit) {
        this.repository = repository;
        this.accessCatalog = accessCatalog;
        this.storeDataScope = storeDataScope;
        this.numbers = numbers;
        this.audit = audit;
    }

    public PageResult<Announcement> announcements(
            Long storeId, String keyword, String status, int page, int size) {
        Page pageValue = page(page, size);
        Long constrainedStore = storeDataScope.constrainNullable(storeId);
        return repository.findAnnouncements(
                constrainedStore, optional(keyword, 200, "查询关键字"),
                optionalEnum(status, ANNOUNCEMENT_STATUSES, "公告状态无效"),
                pageValue.page(), pageValue.size());
    }

    public Announcement announcement(long id) {
        Announcement value = repository.findAnnouncement(id)
                .orElseThrow(() -> new ResourceNotFoundException("通知公告不存在"));
        requireAnnouncementAccess(value);
        return value;
    }

    @Transactional
    public Announcement createAnnouncement(CreateAnnouncementRequest request, String username) {
        UserIdentity operator = accessCatalog.userIdentity(username);
        NormalizedAnnouncement value = normalizeAnnouncement(
                request.title(), request.body(), request.scopeType(), request.storeIds(),
                request.validFrom(), request.validTo(), request.priority(), request.pinned(), request.status());
        Announcement created = repository.createAnnouncement(new NewAnnouncement(
                numbers.next(), value.title(), value.body(), value.scopeType(), value.storeIds(),
                value.validFrom(), value.validTo(), value.priority(), value.pinned(), value.status(), operator.id()));
        audit.record("NOTIFICATION", "CREATE", "ANNOUNCEMENT", created.id(), auditStore(created),
                null, snapshot(created), operator.id());
        return created;
    }

    @Transactional
    public Announcement updateAnnouncement(long id, UpdateAnnouncementRequest request, String username) {
        Announcement before = announcement(id);
        validateTransition(before.status(), request.status());
        UserIdentity operator = accessCatalog.userIdentity(username);
        NormalizedAnnouncement value = normalizeAnnouncement(
                request.title(), request.body(), request.scopeType(), request.storeIds(),
                request.validFrom(), request.validTo(), request.priority(), request.pinned(), request.status());
        Announcement updated = repository.updateAnnouncement(new AnnouncementUpdate(
                id, value.title(), value.body(), value.scopeType(), value.storeIds(), value.validFrom(),
                value.validTo(), value.priority(), value.pinned(), value.status(), request.version(), operator.id()));
        audit.record("NOTIFICATION", "UPDATE", "ANNOUNCEMENT", id, auditStore(updated),
                snapshot(before), snapshot(updated), operator.id());
        return updated;
    }

    public PageResult<NotificationItem> notifications(
            String messageType, String readStatus, LocalDate publishedFrom, LocalDate publishedTo,
            int page, int size, String username) {
        if (publishedFrom != null && publishedTo != null && publishedTo.isBefore(publishedFrom)) {
            throw new IllegalArgumentException("通知结束日期不能早于开始日期");
        }
        UserIdentity user = accessCatalog.userIdentity(username);
        long storeId = storeDataScope.resolveRequired(null);
        Page pageValue = page(page, size);
        return repository.findNotifications(new NotificationQuery(
                user.id(), storeId,
                optionalEnum(messageType, MESSAGE_TYPES, "通知类型无效"),
                optionalEnum(readStatus, Set.of("READ", "UNREAD"), "已读状态无效"),
                publishedFrom == null ? null : publishedFrom.atStartOfDay(),
                publishedTo == null ? null : publishedTo.plusDays(1).atStartOfDay(),
                LocalDateTime.now(), pageValue.page(), pageValue.size()));
    }

    public NotificationItem notification(long id, String username) {
        UserIdentity user = accessCatalog.userIdentity(username);
        return repository.findNotification(
                        id, user.id(), storeDataScope.resolveRequired(null), LocalDateTime.now())
                .orElseThrow(() -> new ResourceNotFoundException("通知不存在"));
    }

    @Transactional
    public NotificationItem markRead(long id, String username) {
        UserIdentity user = accessCatalog.userIdentity(username);
        return repository.markRead(id, user.id(), storeDataScope.resolveRequired(null), LocalDateTime.now());
    }

    @Transactional
    public ReadAllNotificationsResult markAllRead(String messageType, String username) {
        UserIdentity user = accessCatalog.userIdentity(username);
        String type = optionalEnum(messageType, MESSAGE_TYPES, "通知类型无效");
        LocalDateTime now = LocalDateTime.now();
        int count = repository.markAllRead(new NotificationQuery(
                user.id(), storeDataScope.resolveRequired(null), type, "UNREAD",
                null, null, now, 1, 100));
        return new ReadAllNotificationsResult(count);
    }

    public UnreadNotificationCount unreadCount(String username) {
        UserIdentity user = accessCatalog.userIdentity(username);
        return new UnreadNotificationCount(repository.unreadCount(
                user.id(), storeDataScope.resolveRequired(null), LocalDateTime.now()));
    }

    public List<NotificationTemplate> templates(String keyword, String status) {
        return repository.findTemplates(
                optional(keyword, 200, "查询关键字"),
                optionalEnum(status, TEMPLATE_STATUSES, "模板状态无效"));
    }

    public NotificationTemplate template(long id) {
        return repository.findTemplate(id)
                .orElseThrow(() -> new ResourceNotFoundException("通知模板不存在"));
    }

    @Transactional
    public NotificationTemplate createTemplate(CreateNotificationTemplateRequest request, String username) {
        UserIdentity operator = accessCatalog.userIdentity(username);
        NormalizedTemplate value = normalizeTemplate(
                request.eventCode(), request.eventName(), request.titleTemplate(), request.bodyTemplate(),
                request.variables(), request.status());
        NotificationTemplate created = repository.createTemplate(new NewNotificationTemplate(
                value.eventCode(), value.eventName(), value.title(), value.body(), value.variablesCsv(),
                value.status(), operator.id()));
        audit.record("NOTIFICATION", "CREATE", "NOTIFICATION_TEMPLATE", created.id(), null,
                null, templateSnapshot(created), operator.id());
        return created;
    }

    @Transactional
    public NotificationTemplate updateTemplate(
            long id, UpdateNotificationTemplateRequest request, String username) {
        NotificationTemplate before = template(id);
        UserIdentity operator = accessCatalog.userIdentity(username);
        NormalizedTemplate value = normalizeTemplate(
                before.eventCode(), request.eventName(), request.titleTemplate(), request.bodyTemplate(),
                request.variables(), request.status());
        NotificationTemplate updated = repository.updateTemplate(new NotificationTemplateUpdate(
                id, value.eventName(), value.title(), value.body(), value.variablesCsv(), value.status(),
                request.version(), operator.id()));
        audit.record("NOTIFICATION", "UPDATE", "NOTIFICATION_TEMPLATE", id, null,
                templateSnapshot(before), templateSnapshot(updated), operator.id());
        return updated;
    }

    @Transactional
    public TestNotificationResult sendTest(SendTestNotificationRequest request, String username) {
        NotificationTemplate template = template(request.templateId());
        if (!"ACTIVE".equals(template.status())) throw new IllegalArgumentException("停用模板不能测试发送");
        Map<String, String> values = normalizeValues(template, request.variables());
        String title = render(template.titleTemplate(), values, 100, "通知标题");
        String body = render(template.bodyTemplate(), values, 4000, "通知正文");
        UserIdentity operator = accessCatalog.userIdentity(username);
        String notificationNo = numbers.next();
        repository.publish(new BusinessNotificationDraft(
                notificationNo, "SYSTEM", "TEMPLATE_TEST", title, body,
                storeDataScope.resolveRequired(null), "TEMPLATE_TEST", positiveRandomId(notificationNo),
                null, 0, operator.id()));
        return new TestNotificationResult(notificationNo, title, body);
    }

    @Transactional
    public void publishBillReversal(BillDetail bill, ReversalDetail reversal, long operatorId) {
        NotificationTemplate template = repository.findActiveTemplate("BILL_REVERSAL").orElse(null);
        if (template == null) return;
        Map<String, String> values = Map.of(
                "billNo", bill.bill().billNo(),
                "reversalNo", reversal.reversal().reversalNo(),
                "storeName", bill.bill().storeName(),
                "refundAmount", amount(reversal.reversal().refundAmount()),
                "reason", reversal.reversal().reason());
        repository.publish(new BusinessNotificationDraft(
                numbers.next(), "BILL_REVERSAL", "BILL_REVERSAL",
                render(template.titleTemplate(), values, 100, "通知标题"),
                render(template.bodyTemplate(), values, 4000, "通知正文"),
                bill.bill().storeId(), "REVERSAL", reversal.reversal().id(),
                "/app/settlement/reversals?reversalId=" + reversal.reversal().id(), 100, operatorId));
    }

    private NormalizedAnnouncement normalizeAnnouncement(
            String title, String body, String scopeType, List<Long> storeIds,
            LocalDateTime validFrom, LocalDateTime validTo, int priority, boolean pinned, String status) {
        String scope = requiredEnum(scopeType, Set.of("ALL", "STORES"), "公告范围无效");
        List<Long> stores = storeIds == null ? List.of() : new ArrayList<>(new LinkedHashSet<>(storeIds));
        stores.sort(Long::compareTo);
        if ("ALL".equals(scope)) {
            if (!stores.isEmpty()) throw new IllegalArgumentException("全部门店公告不能再指定门店");
            storeDataScope.requireAllStoreAccess();
        } else {
            if (stores.isEmpty()) throw new IllegalArgumentException("指定门店公告至少选择一个门店");
            stores.forEach(storeDataScope::require);
        }
        if (validFrom != null && validTo != null && !validTo.isAfter(validFrom)) {
            throw new IllegalArgumentException("公告结束时间必须晚于开始时间");
        }
        return new NormalizedAnnouncement(
                title.trim(), body.trim(), scope, stores, validFrom, validTo, priority, pinned,
                requiredEnum(status, ANNOUNCEMENT_STATUSES, "公告状态无效"));
    }

    private NormalizedTemplate normalizeTemplate(
            String eventCode, String eventName, String title, String body, List<String> variables, String status) {
        String code = eventCode.trim().toUpperCase(Locale.ROOT);
        if (!EVENT_CODE.matcher(code).matches()) throw new IllegalArgumentException("事件编码格式无效");
        LinkedHashSet<String> normalizedVariables = new LinkedHashSet<>();
        for (String variable : variables) {
            String value = variable.trim();
            if (!VARIABLE.matcher(value).matches()) throw new IllegalArgumentException("模板变量格式无效：" + value);
            normalizedVariables.add(value);
        }
        validatePlaceholders(title, normalizedVariables);
        validatePlaceholders(body, normalizedVariables);
        String variablesCsv = String.join(",", normalizedVariables);
        if (variablesCsv.length() > 1000) throw new IllegalArgumentException("模板变量清单不能超过1000个字符");
        return new NormalizedTemplate(
                code, eventName.trim(), title.trim(), body.trim(), variablesCsv,
                requiredEnum(status, TEMPLATE_STATUSES, "模板状态无效"));
    }

    private static void validatePlaceholders(String template, Set<String> variables) {
        Matcher matcher = PLACEHOLDER.matcher(template);
        while (matcher.find()) {
            if (!variables.contains(matcher.group(1))) {
                throw new IllegalArgumentException("模板使用了未声明变量：" + matcher.group(1));
            }
        }
        String stripped = matcher.reset().replaceAll("");
        if (stripped.contains("{{") || stripped.contains("}}")) {
            throw new IllegalArgumentException("模板变量占位符格式无效");
        }
    }

    private static Map<String, String> normalizeValues(
            NotificationTemplate template, Map<String, String> values) {
        Set<String> allowed = Set.copyOf(template.variables());
        if (!allowed.equals(values.keySet())) throw new IllegalArgumentException("测试变量必须与模板声明完全一致");
        Map<String, String> normalized = new LinkedHashMap<>();
        values.forEach((key, value) -> normalized.put(key, value == null ? "" : value.trim()));
        return normalized;
    }

    private static String render(
            String template, Map<String, String> values, int maximumLength, String label) {
        String rendered = template;
        for (Map.Entry<String, String> entry : values.entrySet()) {
            rendered = rendered.replace("{{" + entry.getKey() + "}}", entry.getValue());
        }
        Matcher remaining = PLACEHOLDER.matcher(rendered);
        if (remaining.find()) throw new IllegalArgumentException("模板变量缺少取值：" + remaining.group(1));
        if (rendered.length() > maximumLength) {
            throw new IllegalArgumentException(label + "渲染后不能超过" + maximumLength + "个字符");
        }
        return rendered;
    }

    private void requireAnnouncementAccess(Announcement announcement) {
        if ("STORES".equals(announcement.scopeType())) storeDataScope.requireAny(announcement.storeIds());
    }

    private static void validateTransition(String current, String target) {
        String normalized = requiredEnum(target, ANNOUNCEMENT_STATUSES, "公告状态无效");
        if (("PUBLISHED".equals(current) || "DISABLED".equals(current)) && "DRAFT".equals(normalized)) {
            throw new IllegalArgumentException("已发布公告不能退回草稿状态");
        }
    }

    private static Long auditStore(Announcement announcement) {
        return "STORES".equals(announcement.scopeType()) && announcement.storeIds().size() == 1
                ? announcement.storeIds().getFirst() : null;
    }

    private static Map<String, Object> snapshot(Announcement value) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("title", value.title());
        result.put("scopeType", value.scopeType());
        result.put("storeIds", value.storeIds());
        result.put("validFrom", value.validFrom());
        result.put("validTo", value.validTo());
        result.put("priority", value.priority());
        result.put("pinned", value.pinned());
        result.put("status", value.status());
        return result;
    }

    private static Map<String, Object> templateSnapshot(NotificationTemplate value) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("eventCode", value.eventCode());
        result.put("eventName", value.eventName());
        result.put("titleTemplate", value.titleTemplate());
        result.put("bodyTemplate", value.bodyTemplate());
        result.put("variables", value.variables());
        result.put("status", value.status());
        return result;
    }

    private static String amount(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    private static long positiveRandomId(String value) {
        return Long.parseLong(value.substring(3, 18), 16);
    }

    private static Page page(int page, int size) {
        if (page < 1) throw new IllegalArgumentException("页码必须从1开始");
        if (size < 1 || size > 100) throw new IllegalArgumentException("每页数量必须在1到100之间");
        return new Page(page, size);
    }

    private static String optional(String value, int max, String label) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim();
        if (normalized.length() > max) throw new IllegalArgumentException(label + "不能超过" + max + "个字符");
        return normalized;
    }

    private static String optionalEnum(String value, Set<String> supported, String message) {
        return value == null || value.isBlank() ? null : requiredEnum(value, supported, message);
    }

    private static String requiredEnum(String value, Set<String> supported, String message) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(message);
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!supported.contains(normalized)) throw new IllegalArgumentException(message);
        return normalized;
    }

    private record Page(int page, int size) {
    }

    private record NormalizedAnnouncement(
            String title, String body, String scopeType, List<Long> storeIds,
            LocalDateTime validFrom, LocalDateTime validTo, int priority, boolean pinned, String status) {
    }

    private record NormalizedTemplate(
            String eventCode, String eventName, String title, String body, String variablesCsv, String status) {
    }
}
