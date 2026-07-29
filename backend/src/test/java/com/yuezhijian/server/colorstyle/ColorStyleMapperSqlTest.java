package com.yuezhijian.server.colorstyle;

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

class ColorStyleMapperSqlTest {
    @Test
    void styleQueryParsesCategoryKeywordAndStatusFilters() throws Exception {
        Method method = ColorStyleMapper.class.getMethod(
                "findStyles", Long.class, String.class, String.class, int.class, int.class);
        String script = String.join(" ", method.getAnnotation(Select.class).value());
        SqlSource source = new XMLLanguageDriver().createSqlSource(new Configuration(), script, Map.class);
        BoundSql sql = source.getBoundSql(new HashMap<>(Map.of(
                "categoryId", 1L, "keyword", "红", "status", "ACTIVE", "offset", 0, "size", 20)));

        assertThat(sql.getSql()).contains(
                "EXISTS", "assignment.category_id = ?",
                "style.color_code LIKE CONCAT('%', ?, '%')", "style.status = ?",
                "OFFSET ? ROWS FETCH NEXT ? ROWS ONLY");
        assertThat(sql.getParameterMappings()).hasSize(6);
    }

    @Test
    void categoryStyleAndAssetUpdatesUseRowVersion() throws Exception {
        Method category = ColorStyleMapper.class.getMethod(
                "updateCategory", ColorStyleCategoryUpdate.class);
        Method style = ColorStyleMapper.class.getMethod("updateStyle", ColorStyleUpdate.class);
        Method asset = ColorStyleMapper.class.getMethod("updateAsset", ColorStyleAssetUpdate.class);

        assertThat(String.join(" ", category.getAnnotation(Update.class).value()))
                .contains("row_version = CONVERT(binary(8), #{version}, 1)", "parent_id = #{parentId}");
        assertThat(String.join(" ", style.getAnnotation(Update.class).value()))
                .contains("row_version = CONVERT(binary(8), #{version}, 1)", "updated_by = #{operatorId}");
        assertThat(String.join(" ", asset.getAnnotation(Update.class).value()))
                .contains("color_style_id = #{colorStyleId}",
                        "row_version = CONVERT(binary(8), #{version}, 1)");
        Method lock = ColorStyleMapper.class.getMethod("activeAssetCountForUpdate", long.class);
        assertThat(String.join(" ", lock.getAnnotation(Select.class).value()))
                .contains("WITH (UPDLOCK, HOLDLOCK)", "status = 'ACTIVE'");
    }
}
