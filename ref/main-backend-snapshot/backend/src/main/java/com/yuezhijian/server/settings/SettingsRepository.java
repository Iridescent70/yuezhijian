package com.yuezhijian.server.settings;

import java.util.List;
import java.util.Optional;

public interface SettingsRepository {
    List<SystemParameterItem> parameters(String group);

    Optional<SystemParameterItem> findParameter(String group, String key);

    SystemParameterItem updateParameter(
            long id, String value, String status, String version, long operatorId);

    List<SatisfactionRuleRow> satisfactionRules(String status);

    Optional<SatisfactionRuleRow> findSatisfactionRule(long id);

    boolean existsSatisfactionRuleName(String name, Long excludeId);

    SatisfactionRuleRow createSatisfactionRule(SatisfactionRuleDraft draft);

    SatisfactionRuleRow updateSatisfactionRule(SatisfactionRuleUpdate update);
}
