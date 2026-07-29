package com.yuezhijian.server.settings;

import java.util.Map;

public record SatisfactionRuleTestResult(
        boolean matched,
        Long ruleId,
        String ruleName,
        String matchedKeyword,
        Integer score,
        Map<String, String> componentMapping,
        String message) {
}
