package com.yuezhijian.server.settings;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public record SatisfactionRule(
        long id,
        String ruleName,
        List<String> keywords,
        int score,
        Map<String, String> componentMapping,
        int priority,
        String status,
        LocalDateTime updatedAt,
        String version) {
}
