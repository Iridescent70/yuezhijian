package com.yuezhijian.server.asset;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.SqlSource;
import org.apache.ibatis.scripting.xmltags.XMLLanguageDriver;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

class CardMapperSqlTest {
    @Test
    void cardTypeSearchCanParseAllFilters() throws Exception {
        Method method = CardMapper.class.getMethod("searchCardTypes", Long.class, String.class, String.class);
        String script = String.join(" ", method.getAnnotation(Select.class).value());
        SqlSource source = new XMLLanguageDriver().createSqlSource(new Configuration(), script, Map.class);
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("storeId", 2L);
        parameters.put("keyword", "NAIL");
        parameters.put("status", "ACTIVE");

        BoundSql sql = source.getBoundSql(parameters);

        assertThat(sql.getSql()).contains("cfg.store_id = ?", "card_type_name LIKE", "type.status = ?");
        assertThat(sql.getParameterMappings()).hasSize(4);
    }
}
