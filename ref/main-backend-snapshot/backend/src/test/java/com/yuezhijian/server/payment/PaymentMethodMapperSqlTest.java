package com.yuezhijian.server.payment;

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

class PaymentMethodMapperSqlTest {
    @Test
    void managementQueriesAndOptimisticUpdatesCanBeParsed() throws Exception {
        BoundSql methods = parse(
                PaymentMethodMapper.class.getMethod(
                        "findMethods", String.class, String.class, String.class),
                Map.of("keyword", "微信", "type", "WECHAT", "status", "ACTIVE"));
        assertThat(methods.getSql())
                .contains("method.method_code LIKE")
                .contains("method.method_name LIKE")
                .contains("method.method_type = ?")
                .contains("method.status = ?")
                .contains("CONVERT(varchar(18), CAST(method.row_version AS varbinary(8)), 1)");
        assertThat(methods.getParameterMappings()).hasSize(4);

        BoundSql stores = parse(
                PaymentMethodMapper.class.getMethod("findStores", long.class, Long.class),
                Map.of("paymentMethodId", 3L, "storeId", 2L));
        assertThat(stores.getSql())
                .contains("LEFT JOIN dbo.cat_payment_method_store")
                .contains("config.payment_method_id = ?")
                .contains("store.id = ?")
                .contains("config.row_version");
        assertThat(stores.getParameterMappings()).hasSize(2);

        Update master = PaymentMethodMapper.class
                .getMethod("updateMethod", PaymentMethodUpdate.class).getAnnotation(Update.class);
        assertThat(String.join(" ", master.value()))
                .contains("row_version = CONVERT(varbinary(8), #{version}, 1)");
        Update store = PaymentMethodMapper.class
                .getMethod("updateStore", PaymentMethodStoreUpdate.class).getAnnotation(Update.class);
        assertThat(String.join(" ", store.value()))
                .contains("row_version = CONVERT(varbinary(8), #{version}, 1)");
    }

    private BoundSql parse(Method method, Map<String, Object> parameters) {
        Select select = method.getAnnotation(Select.class);
        SqlSource source = new XMLLanguageDriver().createSqlSource(
                new Configuration(), String.join(" ", select.value()), Map.class);
        return source.getBoundSql(new HashMap<>(parameters));
    }
}
