package com.yuezhijian.server.settings;

import com.yuezhijian.server.common.DuplicateResourceException;
import com.yuezhijian.server.common.ResourceNotFoundException;
import java.util.List;
import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("sqlserver")
public class SqlServerSettingsRepository implements SettingsRepository {
    private final SettingsMapper mapper;

    public SqlServerSettingsRepository(SettingsMapper mapper) { this.mapper = mapper; }

    @Override
    public List<SystemParameterItem> parameters(String group) { return mapper.findParameters(group); }

    @Override
    public Optional<SystemParameterItem> findParameter(String group, String key) {
        return Optional.ofNullable(mapper.findParameter(group, key));
    }

    @Override
    public SystemParameterItem updateParameter(
            long id, String value, String status, String version, long operatorId) {
        if (mapper.updateParameter(id, value, status, version, operatorId) != 1) {
            throw new DuplicateResourceException("系统参数已被他人修改，请刷新后重试");
        }
        return Optional.ofNullable(mapper.findParameterById(id))
                .orElseThrow(() -> new ResourceNotFoundException("系统参数不存在"));
    }

    @Override
    public List<SatisfactionRuleRow> satisfactionRules(String status) {
        return mapper.findSatisfactionRules(status);
    }

    @Override
    public Optional<SatisfactionRuleRow> findSatisfactionRule(long id) {
        return Optional.ofNullable(mapper.findSatisfactionRule(id));
    }

    @Override
    public boolean existsSatisfactionRuleName(String name, Long excludeId) {
        return mapper.countSatisfactionRuleName(name, excludeId) > 0;
    }

    @Override
    public SatisfactionRuleRow createSatisfactionRule(SatisfactionRuleDraft draft) {
        if (existsSatisfactionRuleName(draft.ruleName(), null)) {
            throw new DuplicateResourceException("满意度规则名称已存在");
        }
        mapper.insertSatisfactionRule(draft);
        return Optional.ofNullable(mapper.findSatisfactionRuleByName(draft.ruleName())).orElseThrow();
    }

    @Override
    public SatisfactionRuleRow updateSatisfactionRule(SatisfactionRuleUpdate update) {
        if (existsSatisfactionRuleName(update.ruleName(), update.id())) {
            throw new DuplicateResourceException("满意度规则名称已存在");
        }
        if (mapper.updateSatisfactionRule(update) != 1) {
            throw new DuplicateResourceException("满意度规则已被他人修改，请刷新后重试");
        }
        return findSatisfactionRule(update.id())
                .orElseThrow(() -> new ResourceNotFoundException("满意度规则不存在"));
    }
}
