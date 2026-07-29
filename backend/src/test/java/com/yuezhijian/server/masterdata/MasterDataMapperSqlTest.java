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

    @Test
    void employeeAndWorkstationUpdatesUseRowVersion() throws Exception {
        Update employee = MasterDataMapper.class
                .getMethod("updateEmployee", ProtectedEmployeeUpdate.class).getAnnotation(Update.class);
        assertThat(String.join(" ", employee.value()))
                .contains("row_version = CONVERT(binary(8), #{version}, 1)")
                .contains("mobile_ciphertext = #{mobileCiphertext}")
                .contains("updated_by = #{updatedBy}");

        Update workstation = MasterDataMapper.class
                .getMethod("updateWorkstation", WorkstationUpdate.class).getAnnotation(Update.class);
        assertThat(String.join(" ", workstation.value()))
                .contains("row_version = CONVERT(binary(8), #{version}, 1)")
                .contains("status = #{status}")
                .contains("updated_by = #{updatedBy}");
    }

    @Test
    void positionQueriesFilterActiveAndUpdatesUseRowVersion() throws Exception {
        BoundSql positions = parse(
                MasterDataMapper.class.getMethod("findPositions", boolean.class),
                Map.of("activeOnly", true));
        assertThat(positions.getSql()).contains("WHERE status = 'ACTIVE'");

        Update update = MasterDataMapper.class
                .getMethod("updatePosition", PositionUpdate.class).getAnnotation(Update.class);
        assertThat(String.join(" ", update.value()))
                .contains("row_version = CONVERT(binary(8), #{version}, 1)")
                .contains("default_service_rate = #{defaultServiceRate}")
                .contains("updated_by = #{updatedBy}");
    }

    @Test
    void categoryAndUnitQueriesFilterActiveAndUpdatesUseRowVersion() throws Exception {
        BoundSql categories = parse(
                MasterDataMapper.class.getMethod("findCategories", String.class, boolean.class),
                Map.of("type", "PRODUCT", "activeOnly", true));
        assertThat(categories.getSql())
                .contains("category_type = ?")
                .contains("status = 'ACTIVE'")
                .contains("row_version");
        BoundSql units = parse(
                MasterDataMapper.class.getMethod("findUnits", boolean.class),
                Map.of("activeOnly", false));
        assertThat(units.getSql()).doesNotContain("WHERE status = 'ACTIVE'").contains("row_version");

        Update categoryUpdate = MasterDataMapper.class
                .getMethod("updateCategory", CategoryUpdate.class).getAnnotation(Update.class);
        assertThat(String.join(" ", categoryUpdate.value()))
                .contains("row_version = CONVERT(binary(8), #{version}, 1)")
                .contains("sort_no = #{sortNo}");
        Update unitUpdate = MasterDataMapper.class
                .getMethod("updateUnit", UnitUpdate.class).getAnnotation(Update.class);
        assertThat(String.join(" ", unitUpdate.value()))
                .contains("row_version = CONVERT(binary(8), #{version}, 1)")
                .contains("decimal_places = #{decimalPlaces}");
    }

    private BoundSql parse(Method method, Map<String, Object> parameters) {
        Select select = method.getAnnotation(Select.class);
        String script = String.join(" ", select.value());
        SqlSource sqlSource = new XMLLanguageDriver()
                .createSqlSource(new Configuration(), script, Map.class);
        return sqlSource.getBoundSql(new HashMap<>(parameters));
    }
}
