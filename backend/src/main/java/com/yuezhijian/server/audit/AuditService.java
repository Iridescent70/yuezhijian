package com.yuezhijian.server.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuezhijian.server.common.TraceIds;
import java.math.BigDecimal;
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
                traceId, operatorId, storeId, module, action, normalizeType(objectType),
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
        String normalized = objectType.trim().toUpperCase(Locale.ROOT);
        if (!OBJECT_TYPES.contains(normalized)) throw new IllegalArgumentException("不支持的操作历史对象类型");
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

    private String display(JsonNode value) {
        if (value == null || value.isNull()) return null;
        if (value.isBoolean()) return value.asBoolean() ? "是" : "否";
        if (value.isNumber()) return new BigDecimal(value.asText()).stripTrailingZeros().toPlainString();
        String text = value.asText();
        return switch (text) {
            case "ACTIVE" -> "启用";
            case "DISABLED" -> "停用";
            case "ON_SALE" -> "在售";
            case "OFF_SALE" -> "未上架";
            default -> text;
        };
    }

    private static Map<String, String> orderedLabels(String... values) {
        Map<String, String> labels = new LinkedHashMap<>();
        for (int index = 0; index < values.length; index += 2) labels.put(values[index], values[index + 1]);
        return Collections.unmodifiableMap(labels);
    }
}
