package com.yuezhijian.server.settings;

public record SatisfactionRuleUpdate(
        long id,
        String ruleName,
        String keywordPattern,
        int score,
        String componentMappingJson,
        int priority,
        String status,
        String version,
        long operatorId) {
}
