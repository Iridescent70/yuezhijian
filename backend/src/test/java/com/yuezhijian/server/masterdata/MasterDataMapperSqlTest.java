package com.yuezhijian.server.masterdata;

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

class MasterDataMapperSqlTest {
    @Test
    void employeeAndServiceDynamicQueriesCanBeParsed() throws Exception {
        BoundSql employeeSql = parse(
                MasterDataMapper.class.getMethod("findEmployees", Long.class, String.class),
                Map.of("storeId", 2L, "keyword", "技师"));
        assertThat(employeeSql.getSql()).contains("e.primary_store_id = ?");
        assertThat(employeeSql.getSql()).contains("e.name LIKE CONCAT('%', ?, '%')");

        BoundSql serviceSql = parse(
                MasterDataMapper.class.getMethod("findServices", Long.class, String.class),
                Map.of("storeId", 2L, "keyword", "美甲"));
        assertThat(serviceSql.getSql()).contains("item_store.store_id = ?");
        assertThat(serviceSql.getSql()).contains("store_cfg.store_id IS NOT NULL");
        assertThat(serviceSql.getParameterMappings()).hasSize(3);
    }

    @Test
    void serviceUpdatesUseRowVersionAndStoreIdentity() throws Exception {
        Update service = MasterDataMapper.class.getMethod("updateService", ServiceItemUpdate.class)
                .getAnnotation(Update.class);
        assertThat(String.join(" ", service.value()))
                .contains("row_version = CONVERT(binary(8), #{version}, 1)")
                .contains("updated_by = #{updatedBy}");

        Update store = MasterDataMapper.class.getMethod("updateServiceStore", ServiceItemUpdate.class)
                .getAnnotation(Update.class);
        assertThat(String.join(" ", store.value()))
                .contains("item_type = 'SERVICE'")
                .contains("store_id = #{update.storeId}");
    }

    private BoundSql parse(Method method, Map<String, Object> parameters) {
        Select select = method.getAnnotation(Select.class);
        String script = String.join(" ", select.value());
        SqlSource sqlSource = new XMLLanguageDriver()
                .createSqlSource(new Configuration(), script, Map.class);
        return sqlSource.getBoundSql(new HashMap<>(parameters));
    }
}
