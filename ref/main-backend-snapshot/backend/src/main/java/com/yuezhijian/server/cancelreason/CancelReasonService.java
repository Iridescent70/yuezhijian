package com.yuezhijian.server.cancelreason;

import com.yuezhijian.server.audit.AuditService;
import com.yuezhijian.server.common.ResourceNotFoundException;
import com.yuezhijian.server.iam.AccessCatalogService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CancelReasonService {
    private static final Set<String> BUSINESS_TYPES = Set.of("APPOINTMENT", "BILL", "HOME_SERVICE");
    private static final Set<String> STATUSES = Set.of("ACTIVE", "DISABLED");
    private final CancelReasonRepository repository;
    private final AccessCatalogService accessCatalog;
    private final AuditService audit;

    public CancelReasonService(
            CancelReasonRepository repository,
            AccessCatalogService accessCatalog,
            AuditService audit) {
        this.repository = repository;
        this.accessCatalog = accessCatalog;
        this.audit = audit;
    }

    public List<CancelReason> findAll(String businessType, String keyword, String status) {
        return repository.findAll(
                optionalBusinessType(businessType), optional(keyword), optionalStatus(status));
    }

    public CancelReason detail(long id) {
        return repository.find(id).orElseThrow(() -> new ResourceNotFoundException("取消原因不存在"));
    }

    public List<CancelReasonOption> options(String businessType) {
        String normalized = businessType(businessType);
        return repository.findAll(normalized, null, "ACTIVE").stream()
                .map(reason -> new CancelReasonOption(reason.code(), reason.name(), reason.requiresNote()))
                .toList();
    }

    public CancelReason requireActive(String businessType, String code, String note) {
        String normalizedType = businessType(businessType);
        String normalizedCode = code(code);
        CancelReason reason = repository.findActive(normalizedType, normalizedCode)
                .orElseThrow(() -> new IllegalArgumentException("取消原因不存在或已停用"));
        if (reason.requiresNote() && (note == null || note.isBlank())) {
            throw new IllegalArgumentException("所选原因必须填写说明");
        }
        return reason;
    }

    @Transactional
    public CancelReason create(CreateCancelReasonRequest request, String username) {
        long operatorId = accessCatalog.userIdentity(username).id();
        CancelReason created = repository.create(new NewCancelReason(
                businessType(request.businessType()), code(request.code()), request.name().trim(),
                request.requiresNote(), request.sortNo(), operatorId));
        audit.record("SYSTEM", "CREATE", "CANCEL_REASON", created.id(), null,
                null, snapshot(created), operatorId);
        return created;
    }

    @Transactional
    public CancelReason update(long id, UpdateCancelReasonRequest request, String username) {
        CancelReason before = detail(id);
        long operatorId = accessCatalog.userIdentity(username).id();
        CancelReason updated = repository.update(new CancelReasonUpdate(
                id, request.name().trim(), request.requiresNote(), request.sortNo(),
                status(request.status()), request.version(), operatorId));
        audit.record("SYSTEM", "UPDATE", "CANCEL_REASON", id, null,
                snapshot(before), snapshot(updated), operatorId);
        return updated;
    }

    private Map<String, Object> snapshot(CancelReason reason) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("businessType", reason.businessType());
        value.put("code", reason.code());
        value.put("name", reason.name());
        value.put("requiresNote", reason.requiresNote());
        value.put("sortNo", reason.sortNo());
        value.put("status", reason.status());
        return value;
    }

    private static String optionalBusinessType(String value) {
        return value == null || value.isBlank() ? null : businessType(value);
    }

    private static String optionalStatus(String value) {
        return value == null || value.isBlank() ? null : status(value);
    }

    private static String businessType(String value) {
        String normalized = normalize(value);
        if (!BUSINESS_TYPES.contains(normalized)) throw new IllegalArgumentException("适用业务无效");
        return normalized;
    }

    private static String status(String value) {
        String normalized = normalize(value);
        if (!STATUSES.contains(normalized)) throw new IllegalArgumentException("取消原因状态无效");
        return normalized;
    }

    private static String code(String value) {
        String normalized = normalize(value);
        if (!normalized.matches("[A-Z][A-Z0-9_]{0,63}")) {
            throw new IllegalArgumentException("原因编号只能使用大写字母、数字和下划线，且必须以字母开头");
        }
        return normalized;
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("必填内容不能为空");
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private static String optional(String value) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim();
        if (normalized.length() > 200) throw new IllegalArgumentException("取消原因查询不能超过200个字符");
        return normalized;
    }
}
