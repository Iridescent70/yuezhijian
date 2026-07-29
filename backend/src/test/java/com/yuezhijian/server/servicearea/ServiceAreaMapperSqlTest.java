package com.yuezhijian.server.servicearea;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.SqlSource;
import org.apache.ibatis.scripting.xmltags.XMLLanguageDriver;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

class ServiceAreaMapperSqlTest {
    @Test
    void filteredQueryAndOptimisticUpdateCanBeParsed() throws Exception {
        BoundSql list = parse(
                ServiceAreaMapper.class.getMethod("findAll", Long.class, String.class, String.class),
                Map.of("storeId", 2L, "keyword", "浦东", "status", "ACTIVE"));
        assertThat(list.getSql())
                .contains("area.store_id = ?")
                .contains("area.city LIKE")
                .contains("area.district LIKE")
                .contains("area.address LIKE")
                .contains("area.status = ?")
                .contains("CONVERT(varchar(18), area.row_version, 1)");
        assertThat(list.getParameterMappings()).hasSize(5);

        Update update = ServiceAreaMapper.class
                .getMethod("update", ServiceAreaUpdate.class).getAnnotation(Update.class);
        assertThat(String.join(" ", update.value()))
                .contains("row_version = CONVERT(binary(8), #{version}, 1)")
                .contains("updated_by = #{operatorId}");
    }

    private BoundSql parse(Method method, Map<String, Object> parameters) {
        Select select = method.getAnnotation(Select.class);
        SqlSource source = new XMLLanguageDriver().createSqlSource(
                new Configuration(), String.join(" ", select.value()), Map.class);
        return source.getBoundSql(new HashMap<>(parameters));
    }
}
