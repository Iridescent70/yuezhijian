package com.yuezhijian.server.settings;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.Map;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.SqlSource;
import org.apache.ibatis.scripting.xmltags.XMLLanguageDriver;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

class SettingsMapperSqlTest {
    @Test
    void parameterAndRuleQueriesParseOptionalFilters() throws Exception {
        Method parameterMethod = SettingsMapper.class.getMethod("findParameters", String.class);
        SqlSource parameterSource = new XMLLanguageDriver().createSqlSource(
                new Configuration(), String.join(" ", parameterMethod.getAnnotation(Select.class).value()), Map.class);
        BoundSql parameterSql = parameterSource.getBoundSql(Map.of("group", "VISIT"));
        assertThat(parameterSql.getSql()).contains("is_secret = 0", "param_group = ?");

        Method ruleMethod = SettingsMapper.class.getMethod("findSatisfactionRules", String.class);
        SqlSource ruleSource = new XMLLanguageDriver().createSqlSource(
                new Configuration(), String.join(" ", ruleMethod.getAnnotation(Select.class).value()), Map.class);
        BoundSql ruleSql = ruleSource.getBoundSql(Map.of("status", "ACTIVE"));
        assertThat(ruleSql.getSql()).contains("WHERE status = ?", "ORDER BY priority, id");
    }

    @Test
    void updatesUseRowVersionAndNeverExposeSecretParameters() throws Exception {
        Method parameterMethod = SettingsMapper.class.getMethod(
                "updateParameter", long.class, String.class, String.class, String.class, long.class);
        String parameterSql = String.join(" ", parameterMethod.getAnnotation(Update.class).value());
        assertThat(parameterSql).contains("is_secret = 0", "row_version = CONVERT(binary(8), #{version}, 1)");

        Method ruleMethod = SettingsMapper.class.getMethod("updateSatisfactionRule", SatisfactionRuleUpdate.class);
        String ruleSql = String.join(" ", ruleMethod.getAnnotation(Update.class).value());
        assertThat(ruleSql).contains("component_mapping_json = #{componentMappingJson}",
                "row_version = CONVERT(binary(8), #{version}, 1)");
    }
}
