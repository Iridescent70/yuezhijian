package com.yuezhijian.server.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuezhijian.server.common.PageResult;
import com.yuezhijian.server.common.ResourceNotFoundException;
import com.yuezhijian.server.common.TraceIds;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class AuditService {
    private static final Set<String> OBJECT_TYPES = Set.of("PRODUCT", "SERVICE");
    private static final Map<String, String> ACTION_LABELS = Map.of(
            "CREATE", "新建资料",
            "IMPORT_CREATE", "导入创建",
            "UPDATE", "修改资料",
            "BATCH_SALE_STATUS", "批量调整销售状态");
    private static final Map<String, String> PRODUCT_FIELDS = orderedLabels(
            "code", "产品编号", "name", "产品名称", "categoryName", "产品分类",
            "unitName", "计量单位", "barcode", "条码", "costPrice", "成本",
            "salePrice", "标准售价", "trackStock", "跟踪库存", "description", "产品说明",
            "status", "资料状态", "storeName", "门店", "storePrice", "门店售价",
            "saleStatus", "销售状态");
    private static final Map<String, String> SERVICE_FIELDS = orderedLabels(
            "code", "项目编号", "name", "项目名称", "categoryName", "服务分类",
            "durationMinutes", "服务时长（分钟）", "costAmount", "服务成本",
            "listPrice", "标准售价", "description", "项目说明", "status", "资料状态",
            "storeName", "门店", "storePrice", "门店售价", "saleStatus", "销售状态");

    private final AuditRepository repository;
    private final ObjectMapper objectMapper;

    public AuditService(AuditRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    public void record(
            String module,
            String action,
            String objectType,
            long objectId,
            Long storeId,
            Map<String, Object> before,
            Map<String, Object> after,
            long operatorId) {
        String traceId = TraceIds.current();
        if (traceId.isBlank()) traceId = TraceIds.next();
        repository.append(new NewAuditEvent(
                traceId, operatorId, storeId, module, action, normalizeRecordType(objectType),
                String.valueOf(objectId), json(before), json(after)));
    }

    public List<OperationHistoryItem> history(
            String objectType, long objectId, List<Long> accessibleStoreIds) {
        String type = normalizeType(objectType);
        Map<String, String> labels = "PRODUCT".equals(type) ? PRODUCT_FIELDS : SERVICE_FIELDS;
        return repository.history(type, String.valueOf(objectId), accessibleStoreIds).stream()
                .map(row -> toItem(row, labels))
                .toList();
    }

    public PageResult<AuditLogSummary> search(
            Long userId,
            String operator,
            String module,
            String action,
            String objectType,
            String objectId,
            String result,
            LocalDate occurredFrom,
            LocalDate occurredTo,
            int page,
            int size) {
        if (occurredFrom != null && occurredTo != null && occurredTo.isBefore(occurredFrom)) {
            throw new IllegalArgumentException("结束日期不能早于开始日期");
        }
        String normalizedResult = optional(result);
        if (normalizedResult != null) {
            normalizedResult = normalizedResult.toUpperCase(Locale.ROOT);
            if (!Set.of("SUCCESS", "FAILURE").contains(normalizedResult)) {
                throw new IllegalArgumentException("审计结果无效");
            }
        }
        AuditLogQuery query = new AuditLogQuery(
                userId, limited(operator, 100, "操作人"), limited(module, 64, "模块"), limited(action, 64, "动作"),
                limited(objectType, 64, "对象类型"), limited(objectId, 64, "对象编号"), normalizedResult,
                occurredFrom == null ? null : occurredFrom.atStartOfDay(),
                occurredTo == null ? null : occurredTo.plusDays(1).atStartOfDay(),
                Math.max(page, 1), Math.min(Math.max(size, 1), 100));
        PageResult<AuditLogRow> resultPage = repository.search(query);
        List<AuditLogSummary> items = resultPage.items().stream().map(this::summary).toList();
        return new PageResult<>(items, resultPage.page(), resultPage.size(), resultPage.total());
    }

    public AuditLogDetail detail(long id) {
        AuditLogRow row = repository.find(id)
                .orElseThrow(() -> new ResourceNotFoundException("操作日志不存在"));
        return new AuditLogDetail(
                row.id(), row.traceId(), row.userId(), row.operatorName(), row.storeId(), row.module(),
                row.action(), row.objectType(), row.objectId(), row.result(), row.errorCode(), row.ip(),
                row.occurredAt(), displayMap(row.beforeJson()), displayMap(row.afterJson()));
    }

    private AuditLogSummary summary(AuditLogRow row) {
        return new AuditLogSummary(
                row.id(), row.traceId(), row.userId(), row.operatorName(), row.storeId(), row.module(),
                row.action(), row.objectType(), row.objectId(), row.result(), row.errorCode(), row.occurredAt());
    }

    private OperationHistoryItem toItem(AuditLogRow row, Map<String, String> labels) {
        Map<String, JsonNode> before = parse(row.beforeJson());
        Map<String, JsonNode> after = parse(row.afterJson());
        LinkedHashSet<String> fields = new LinkedHashSet<>(labels.keySet());
        fields.addAll(before.keySet());
        fields.addAll(after.keySet());
        List<OperationChange> changes = new ArrayList<>();
        for (String field : fields) {
            String beforeValue = display(before.get(field));
            String afterValue = display(after.get(field));
            if (!java.util.Objects.equals(beforeValue, afterValue)) {
                changes.add(new OperationChange(
                        field, labels.getOrDefault(field, field), beforeValue, afterValue));
            }
        }
        return new OperationHistoryItem(
                row.id(), row.action(), ACTION_LABELS.getOrDefault(row.action(), row.action()),
                row.userId(), row.operatorName(), row.storeId(), row.occurredAt(), row.traceId(), changes);
    }

    private String normalizeType(String objectType) {
        String normalized = normalizeRecordType(objectType);
        if (!OBJECT_TYPES.contains(normalized)) throw new IllegalArgumentException("不支持的操作历史对象类型");
        return normalized;
    }

    private String normalizeRecordType(String objectType) {
        if (objectType == null) throw new IllegalArgumentException("审计对象类型不能为空");
        String normalized = objectType.trim().toUpperCase(Locale.ROOT);
        if (!normalized.matches("[A-Z][A-Z0-9_]{0,63}")) {
            throw new IllegalArgumentException("审计对象类型无效");
        }
        return normalized;
    }

    private String json(Map<String, Object> value) {
        if (value == null) return null;
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("操作历史快照序列化失败", exception);
        }
    }

    private Map<String, JsonNode> parse(String value) {
        Map<String, JsonNode> result = new LinkedHashMap<>();
        if (value == null || value.isBlank()) return result;
        try {
            objectMapper.readTree(value).fields().forEachRemaining(entry -> result.put(entry.getKey(), entry.getValue()));
            return result;
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("操作历史快照无法解析", exception);
        }
    }

    private Map<String, String> displayMap(String value) {
        Map<String, String> displayValues = new LinkedHashMap<>();
        parse(value).forEach((key, jsonValue) -> displayValues.put(key, display(jsonValue)));
        return displayValues;
    }

    private String display(JsonNode value) {
        if (value == null || value.isNull()) return null;
        if (value.isBoolean()) return value.asBoolean() ? "是" : "否";
        if (value.isNumber()) return new BigDecimal(value.asText()).stripTrailingZeros().toPlainString();
        if (value.isArray() || value.isObject()) return value.toString();
        String text = value.asText();
        return switch (text) {
            case "ACTIVE" -> "启用";
            case "DISABLED" -> "停用";
            case "ON_SALE" -> "在售";
            case "OFF_SALE" -> "未上架";
            default -> text;
        };
    }

    private String limited(String value, int maxLength, String field) {
        String normalized = optional(value);
        if (normalized != null && normalized.length() > maxLength) {
            throw new IllegalArgumentException(field + "查询不能超过" + maxLength + "个字符");
        }
        return normalized;
    }

    private String optional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static Map<String, String> orderedLabels(String... values) {
        Map<String, String> labels = new LinkedHashMap<>();
        for (int index = 0; index < values.length; index += 2) labels.put(values[index], values[index + 1]);
        return Collections.unmodifiableMap(labels);
    }
}
