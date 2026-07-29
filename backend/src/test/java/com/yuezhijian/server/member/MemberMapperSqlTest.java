package com.yuezhijian.server.member;

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

class MemberMapperSqlTest {
    @Test
    void dynamicMemberSearchSqlCanBeParsedWithRecordParameters() throws Exception {
        Method method = MemberMapper.class.getMethod(
                "findPage", MemberQuery.class, int.class, int.class);
        Select select = method.getAnnotation(Select.class);
        String script = String.join(" ", select.value());
        Configuration configuration = new Configuration();
        SqlSource sqlSource = new XMLLanguageDriver().createSqlSource(configuration, script, Map.class);
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("query", new MemberQuery("13800001001", 2L, "ACTIVE", 1, 20, "hash"));
        parameters.put("offset", 0);
        parameters.put("size", 20);

        BoundSql boundSql = sqlSource.getBoundSql(parameters);

        assertThat(boundSql.getSql()).contains("m.mobile_hash = ?");
        assertThat(boundSql.getSql()).contains("OFFSET ? ROWS FETCH NEXT ? ROWS ONLY");
        assertThat(boundSql.getParameterMappings()).isNotEmpty();
    }
}
