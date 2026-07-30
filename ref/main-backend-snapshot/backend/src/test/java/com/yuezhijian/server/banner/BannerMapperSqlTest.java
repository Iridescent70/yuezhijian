package com.yuezhijian.server.banner;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.SqlSource;
import org.apache.ibatis.scripting.xmltags.XMLLanguageDriver;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

class BannerMapperSqlTest {
    @Test
    void managementQueryCanBeParsedWithAllFilters() throws Exception {
        Method method = BannerMapper.class.getMethod("findAll", String.class, String.class, String.class);
        String script = String.join(" ", method.getAnnotation(Select.class).value());
        SqlSource source = new XMLLanguageDriver().createSqlSource(new Configuration(), script, Map.class);
        BoundSql sql = source.getBoundSql(new HashMap<>(Map.of(
                "positionCode", "PC_HOME", "keyword", "活动", "status", "ACTIVE")));

        assertThat(sql.getSql()).contains(
                "image.status = 'ACTIVE'",
                "banner.position_code = ?",
                "banner.title LIKE CONCAT('%', ?, '%')",
                "banner.status = ?");
        assertThat(sql.getParameterMappings()).hasSize(3);
    }

    @Test
    void activeQueryAndUpdatesProtectValidityAndRowVersion() throws Exception {
        Method active = BannerMapper.class.getMethod("findActive", String.class, LocalDateTime.class);
        Method update = BannerMapper.class.getMethod("update", BannerUpdate.class);
        Method replace = BannerMapper.class.getMethod("replaceImage", BannerImageUpdate.class);
        String activeSql = String.join(" ", active.getAnnotation(Select.class).value());
        String updateSql = String.join(" ", update.getAnnotation(Update.class).value());
        String replaceSql = String.join(" ", replace.getAnnotation(Update.class).value());

        assertThat(activeSql).contains(
                "banner.status = 'ACTIVE'", "banner.valid_from <= #{now}", "banner.valid_to >= #{now}");
        assertThat(updateSql).contains(
                "row_version = CONVERT(binary(8), #{version}, 1)", "updated_by = #{operatorId}");
        assertThat(replaceSql).contains(
                "image_file_id = #{imageFileId}", "row_version = CONVERT(binary(8), #{version}, 1)");
    }
}
