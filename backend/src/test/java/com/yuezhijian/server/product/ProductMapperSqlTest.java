package com.yuezhijian.server.product;

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

class ProductMapperSqlTest {
    @Test
    void listQueryScopesTheStoreAndAppliesFilters() throws Exception {
        Method method = ProductMapper.class.getMethod(
                "findProducts", Long.class, Long.class, String.class, String.class);
        String script = String.join(" ", method.getAnnotation(Select.class).value());
        SqlSource source = new XMLLanguageDriver().createSqlSource(new Configuration(), script, Map.class);
        BoundSql sql = source.getBoundSql(new HashMap<>(Map.of(
                "storeId", 2L, "categoryId", 2L, "saleStatus", "ON_SALE", "keyword", "精华")));

        assertThat(sql.getSql()).contains(
                "cfg.item_type = 'PRODUCT'", "cfg.store_id = ?", "store_cfg.store_id IS NOT NULL",
                "product.category_id = ?", "store_cfg.sale_status = ?", "product.product_name LIKE");
    }

    @Test
    void updatesUseRowVersionAndProductStoreIdentity() throws Exception {
        Update product = ProductMapper.class.getMethod("update", ProductUpdate.class).getAnnotation(Update.class);
        Update store = ProductMapper.class.getMethod("updateStore", ProductUpdate.class).getAnnotation(Update.class);

        assertThat(String.join(" ", product.value()))
                .contains("row_version = CONVERT(binary(8), #{version}, 1)", "updated_by = #{updatedBy}");
        assertThat(String.join(" ", store.value()))
                .contains("item_type = 'PRODUCT'", "item_id = #{update.id}", "store_id = #{update.storeId}");
    }
}
