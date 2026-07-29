package com.yuezhijian.server.settings;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuezhijian.server.common.ResourceNotFoundException;
import com.yuezhijian.server.iam.AccessCatalogService;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SystemSettingsService {
    private static final Set<String> PARAMETER_STATUSES = Set.of("ACTIVE", "DISABLED");
    private static final Set<String> RULE_STATUSES = Set.of("ACTIVE", "DISABLED");
    private static final TypeReference<Map<String, String>> STRING_MAP = new TypeReference<>() { };

    private final SettingsRepository repository;
    private final AccessCatalogService accessCatalog;
    private final ObjectMapper objectMapper;

    public SystemSettingsService(
            SettingsRepository repository, AccessCatalogService accessCatalog, ObjectMapper objectMapper) {
        this.repository = repository;
        this.accessCatalog = accessCatalog;
        this.objectMapper = objectMapper;
    }

    public List<SystemParameterItem> parameters(String group) {
        return repository.parameters(normalizeOptional(group));
    }

    @Transactional
    public SystemParameterItem updateParameter(
            long id, UpdateSystemParameterRequest request, String username) {
        SystemParameterItem current = repository.parameters(null).stream()
                .filter(item -> item.id() == id).findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("系统参数不存在或属于受保护配置"));
        String value = request.value().trim();
        String status = request.status().trim().toUpperCase(Locale.ROOT);
        if (!PARAMETER_STATUSES.contains(status)) throw new IllegalArgumentException("系统参数状态无效");
        validateParameterValue(current, value);
        return repository.updateParameter(
                id, value, status, request.version(), accessCatalog.userIdentity(username).id());
    }

    public int integerValue(String group, String key, int defaultValue, int min, int max) {
        var parameter = repository.findParameter(group, key);
        if (parameter.isEmpty() || !"ACTIVE".equals(parameter.get().status())) return defaultValue;
        try {
            int value = Integer.parseInt(parameter.get().value());
            if (value < min || value > max) throw new NumberFormatException();
            return value;
        } catch (RuntimeException exception) {
            throw new IllegalStateException(group + "/" + key + "系统参数配置无效");
        }
    }

    public List<SatisfactionRule> satisfactionRules(String status) {
        String normalized = normalizeOptional(status);
        if (normalized != null && !RULE_STATUSES.contains(normalized)) {
            throw new IllegalArgumentException("满意度规则状态无效");
        }
        return repository.satisfactionRules(normalized).stream().map(this::toRule).toList();
    }

    @Transactional
    public SatisfactionRule createSatisfactionRule(
            CreateSatisfactionRuleRequest request, String username) {
        RuleFields fields = normalizeRule(
                request.ruleName(), request.keywords(), request.componentMapping(), request.status());
        if (repository.existsSatisfactionRuleName(fields.ruleName(), null)) {
            throw new IllegalArgumentException("满意度规则名称已存在");
        }
        return toRule(repository.createSatisfactionRule(new SatisfactionRuleDraft(
                fields.ruleName(), fields.keywordPattern(), request.score(), fields.componentMappingJson(),
                request.priority(), fields.status(), accessCatalog.userIdentity(username).id())));
    }

    @Transactional
    public SatisfactionRule updateSatisfactionRule(
            long id, UpdateSatisfactionRuleRequest request, String username) {
        repository.findSatisfactionRule(id)
                .orElseThrow(() -> new ResourceNotFoundException("满意度规则不存在"));
        RuleFields fields = normalizeRule(
                request.ruleName(), request.keywords(), request.componentMapping(), request.status());
        if (repository.existsSatisfactionRuleName(fields.ruleName(), id)) {
            throw new IllegalArgumentException("满意度规则名称已存在");
        }
        return toRule(repository.updateSatisfactionRule(new SatisfactionRuleUpdate(
                id, fields.ruleName(), fields.keywordPattern(), request.score(), fields.componentMappingJson(),
                request.priority(), fields.status(), request.version(), accessCatalog.userIdentity(username).id())));
    }

    public SatisfactionRuleTestResult testSatisfactionRule(String text) {
        String sample = text.trim().toLowerCase(Locale.ROOT);
        for (SatisfactionRule rule : satisfactionRules("ACTIVE")) {
            List<String> keywords = rule.keywords().stream()
                    .sorted((left, right) -> Integer.compare(right.length(), left.length())).toList();
            for (String keyword : keywords) {
                if (sample.contains(keyword.toLowerCase(Locale.ROOT))) {
                    return new SatisfactionRuleTestResult(
                            true, rule.id(), rule.ruleName(), keyword, rule.score(),
                            rule.componentMapping(), "命中规则；该结果仅用于规则验证，未写入会员或回访数据");
                }
            }
        }
        return new SatisfactionRuleTestResult(
                false, null, null, null, null, Map.of(), "未命中启用规则，不自动推断评分");
    }

    private void validateParameterValue(SystemParameterItem parameter, String value) {
        if (value.isBlank()) throw new IllegalArgumentException("系统参数值不能为空");
        try {
            switch (parameter.valueType()) {
                case "INTEGER" -> Integer.parseInt(value);
                case "DECIMAL" -> new BigDecimal(value);
                case "BOOLEAN" -> {
                    if (!Set.of("true", "false").contains(value.toLowerCase(Locale.ROOT))) {
                        throw new IllegalArgumentException("布尔参数只能填写true或false");
                    }
                }
                case "JSON" -> objectMapper.readTree(value);
                default -> { }
            }
        } catch (JsonProcessingException | NumberFormatException exception) {
            throw new IllegalArgumentException("参数值与" + parameter.valueType() + "类型不匹配");
        }
        if ("ASSET".equals(parameter.paramGroup()) && "POINTS_PER_YUAN".equals(parameter.paramKey())) {
            requireIntegerRange(value, 1, 100000, "积分抵现比例必须在1至100000之间");
        }
        if ("VISIT".equals(parameter.paramGroup()) && "AFTER_SALE_DUE_HOURS".equals(parameter.paramKey())) {
            requireIntegerRange(value, 1, 720, "回访到期小时必须在1至720之间");
        }
    }

    private RuleFields normalizeRule(
            String ruleName, List<String> keywords, Map<String, String> componentMapping, String status) {
        String name = ruleName.trim();
        String normalizedStatus = status.trim().toUpperCase(Locale.ROOT);
        if (!RULE_STATUSES.contains(normalizedStatus)) throw new IllegalArgumentException("满意度规则状态无效");
        LinkedHashSet<String> normalizedKeywords = new LinkedHashSet<>();
        for (String keyword : keywords) {
            String value = keyword.trim();
            if (value.contains("|")) throw new IllegalArgumentException("识别关键词不能包含竖线字符");
            if (!value.isBlank()) normalizedKeywords.add(value);
        }
        if (normalizedKeywords.isEmpty()) throw new IllegalArgumentException("至少填写一个识别关键词");
        String pattern = String.join("|", normalizedKeywords);
        if (pattern.length() > 500) throw new IllegalArgumentException("识别关键词合计不能超过500个字符");

        Map<String, String> mapping = new TreeMap<>();
        if (componentMapping != null) {
            componentMapping.forEach((key, value) -> mapping.put(key.trim(), value.trim()));
        }
        try {
            return new RuleFields(name, pattern, objectMapper.writeValueAsString(mapping), normalizedStatus);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("组件映射无法保存");
        }
    }

    private SatisfactionRule toRule(SatisfactionRuleRow row) {
        List<String> keywords = new ArrayList<>();
        for (String keyword : row.keywordPattern().split("\\|")) {
            if (!keyword.isBlank()) keywords.add(keyword);
        }
        Map<String, String> mapping = new LinkedHashMap<>();
        try {
            Map<String, String> parsed = objectMapper.readValue(row.componentMappingJson(), STRING_MAP);
            if (parsed != null) mapping.putAll(parsed);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("满意度规则组件映射配置无效");
        }
        return new SatisfactionRule(
                row.id(), row.ruleName(), List.copyOf(keywords), row.score(), Map.copyOf(mapping),
                row.priority(), row.status(), row.updatedAt(), row.version());
    }

    private static String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim().toUpperCase(Locale.ROOT);
    }

    private static void requireIntegerRange(String value, int min, int max, String message) {
        int parsed = Integer.parseInt(value);
        if (parsed < min || parsed > max) throw new IllegalArgumentException(message);
    }

    private record RuleFields(
            String ruleName, String keywordPattern, String componentMappingJson, String status) { }
}
