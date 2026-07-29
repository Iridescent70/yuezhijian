package com.yuezhijian.server.settings;

import java.time.LocalDateTime;

public record SatisfactionRuleRow(
        long id,
        String ruleName,
        String keywordPattern,
        int score,
        String componentMappingJson,
        int priority,
        String status,
        LocalDateTime updatedAt,
        String version) {
}
