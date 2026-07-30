package com.yuezhijian.server.settings;

import com.yuezhijian.server.common.DuplicateResourceException;
import com.yuezhijian.server.common.ResourceNotFoundException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("memory")
public class MemorySettingsRepository implements SettingsRepository {
    private final AtomicLong ruleIds = new AtomicLong();
    private final List<SystemParameterItem> parameters = new ArrayList<>();
    private final List<SatisfactionRuleRow> rules = new ArrayList<>();

    public MemorySettingsRepository() {
        LocalDateTime now = LocalDateTime.now();
        parameters.add(new SystemParameterItem(
                1, "ASSET", "POINTS_PER_YUAN", "100", "INTEGER",
                "积分抵现比例：多少积分抵扣1元", "ACTIVE", now, "1"));
        parameters.add(new SystemParameterItem(
                2, "VISIT", "AFTER_SALE_DUE_HOURS", "24", "INTEGER",
                "账单结算后多少小时生成到期回访", "ACTIVE", now, "1"));
        parameters.add(new SystemParameterItem(
                3, "VISIT", "SERVICE_FEEDBACK_DUE_HOURS", "24", "INTEGER",
                "服务反馈创建或重新打开后多少小时应完成处理", "ACTIVE", now, "1"));
    }

    @Override
    public synchronized List<SystemParameterItem> parameters(String group) {
        return parameters.stream()
                .filter(item -> group == null || item.paramGroup().equals(group))
                .sorted(Comparator.comparing(SystemParameterItem::paramGroup)
                        .thenComparing(SystemParameterItem::paramKey))
                .toList();
    }

    @Override
    public synchronized Optional<SystemParameterItem> findParameter(String group, String key) {
        return parameters.stream()
                .filter(item -> item.paramGroup().equals(group) && item.paramKey().equals(key)).findFirst();
    }

    @Override
    public synchronized SystemParameterItem updateParameter(
            long id, String value, String status, String version, long operatorId) {
        SystemParameterItem current = parameters.stream().filter(item -> item.id() == id).findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("系统参数不存在"));
        if (!current.version().equals(version)) {
            throw new DuplicateResourceException("系统参数已被他人修改，请刷新后重试");
        }
        SystemParameterItem updated = new SystemParameterItem(
                current.id(), current.paramGroup(), current.paramKey(), value, current.valueType(),
                current.description(), status, LocalDateTime.now(), nextVersion(version));
        parameters.removeIf(item -> item.id() == id);
        parameters.add(updated);
        return updated;
    }

    @Override
    public synchronized List<SatisfactionRuleRow> satisfactionRules(String status) {
        return rules.stream().filter(item -> status == null || item.status().equals(status))
                .sorted(Comparator.comparingInt(SatisfactionRuleRow::priority)
                        .thenComparingLong(SatisfactionRuleRow::id))
                .toList();
    }

    @Override
    public synchronized Optional<SatisfactionRuleRow> findSatisfactionRule(long id) {
        return rules.stream().filter(item -> item.id() == id).findFirst();
    }

    @Override
    public synchronized boolean existsSatisfactionRuleName(String name, Long excludeId) {
        String normalized = name.toLowerCase(Locale.ROOT);
        return rules.stream().anyMatch(item -> item.ruleName().toLowerCase(Locale.ROOT).equals(normalized)
                && (excludeId == null || item.id() != excludeId));
    }

    @Override
    public synchronized SatisfactionRuleRow createSatisfactionRule(SatisfactionRuleDraft draft) {
        if (existsSatisfactionRuleName(draft.ruleName(), null)) {
            throw new DuplicateResourceException("满意度规则名称已存在");
        }
        SatisfactionRuleRow created = new SatisfactionRuleRow(
                ruleIds.incrementAndGet(), draft.ruleName(), draft.keywordPattern(), draft.score(),
                draft.componentMappingJson(), draft.priority(), draft.status(), LocalDateTime.now(), "1");
        rules.add(created);
        return created;
    }

    @Override
    public synchronized SatisfactionRuleRow updateSatisfactionRule(SatisfactionRuleUpdate update) {
        SatisfactionRuleRow current = findSatisfactionRule(update.id())
                .orElseThrow(() -> new ResourceNotFoundException("满意度规则不存在"));
        if (!current.version().equals(update.version())) {
            throw new DuplicateResourceException("满意度规则已被他人修改，请刷新后重试");
        }
        if (existsSatisfactionRuleName(update.ruleName(), update.id())) {
            throw new DuplicateResourceException("满意度规则名称已存在");
        }
        SatisfactionRuleRow updated = new SatisfactionRuleRow(
                update.id(), update.ruleName(), update.keywordPattern(), update.score(),
                update.componentMappingJson(), update.priority(), update.status(),
                LocalDateTime.now(), nextVersion(update.version()));
        rules.removeIf(item -> item.id() == update.id());
        rules.add(updated);
        return updated;
    }

    private static String nextVersion(String version) {
        return String.valueOf(Long.parseLong(version) + 1);
    }
}
