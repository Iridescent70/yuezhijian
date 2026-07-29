package com.yuezhijian.server.settings;

public record SatisfactionRuleDraft(
        String ruleName,
        String keywordPattern,
        int score,
        String componentMappingJson,
        int priority,
        String status,
        long operatorId) {
}
