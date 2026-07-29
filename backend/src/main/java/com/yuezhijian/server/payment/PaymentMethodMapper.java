package com.yuezhijian.server.payment;

import com.yuezhijian.server.trade.PaymentMethodOption;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface PaymentMethodMapper {
    String METHOD_SELECT = """
            SELECT method.id, method.method_code AS code, method.method_name AS name,
                   method.method_type AS type, method.is_electronic AS electronic,
                   method.included_in_revenue AS includedInRevenue,
                   method.needs_external_ref AS needsExternalReference, method.status,
                   method.updated_at AS updatedAt,
                   CONVERT(varchar(18), method.row_version, 1) AS version
            FROM dbo.cat_payment_method method
            """;

    @Select("""
            SELECT method.id, method.method_code AS code, method.method_name AS name,
                   method.method_type AS type, method.is_electronic AS electronic,
                   method.included_in_revenue AS includedInRevenue,
                   method.needs_external_ref AS needsExternalReference, config.sort_no AS sortNo
            FROM dbo.cat_payment_method_store config
            JOIN dbo.cat_payment_method method ON method.id = config.payment_method_id
            WHERE config.store_id = #{storeId} AND config.enabled = 1 AND method.status = 'ACTIVE'
            ORDER BY config.sort_no, method.id
            """)
    List<PaymentMethodOption> findOptions(long storeId);

    @Select("""
            <script>
            """ + METHOD_SELECT + """
            WHERE 1 = 1
            <if test="keyword != null">
              AND (method.method_code LIKE CONCAT('%', #{keyword}, '%')
                   OR method.method_name LIKE CONCAT('%', #{keyword}, '%'))
            </if>
            <if test="type != null">AND method.method_type = #{type}</if>
            <if test="status != null">AND method.status = #{status}</if>
            ORDER BY method.method_code, method.id
            </script>
            """)
    List<PaymentMethodRow> findMethods(
            @Param("keyword") String keyword,
            @Param("type") String type,
            @Param("status") String status);

    @Select(METHOD_SELECT + " WHERE method.id = #{id}")
    PaymentMethodRow findMethod(long id);

    @Select("""
            <script>
            SELECT store.id AS storeId, store.store_code AS storeCode, store.store_name AS storeName,
                   CASE WHEN config.id IS NULL THEN CAST(0 AS bit) ELSE CAST(1 AS bit) END AS applicable,
                   COALESCE(config.enabled, 0) AS enabled, COALESCE(config.sort_no, 0) AS sortNo,
                   CONVERT(varchar(18), config.row_version, 1) AS version
            FROM dbo.org_store store
            LEFT JOIN dbo.cat_payment_method_store config
              ON config.store_id = store.id AND config.payment_method_id = #{paymentMethodId}
            WHERE store.status = 'ACTIVE'
            <if test="storeId != null">AND store.id = #{storeId}</if>
            ORDER BY store.id
            </script>
            """)
    List<PaymentMethodStoreConfiguration> findStores(
            @Param("paymentMethodId") long paymentMethodId,
            @Param("storeId") Long storeId);

    @Select("""
            SELECT store.id AS storeId, store.store_code AS storeCode, store.store_name AS storeName,
                   CAST(1 AS bit) AS applicable, config.enabled, config.sort_no AS sortNo,
                   CONVERT(varchar(18), config.row_version, 1) AS version
            FROM dbo.cat_payment_method_store config
            JOIN dbo.org_store store ON store.id = config.store_id
            WHERE config.payment_method_id = #{paymentMethodId} AND config.store_id = #{storeId}
            """)
    PaymentMethodStoreConfiguration findConfiguredStore(
            @Param("paymentMethodId") long paymentMethodId,
            @Param("storeId") long storeId);

    @Select("SELECT COUNT(1) FROM dbo.cat_payment_method WHERE method_code = #{code}")
    int countCode(String code);

    @Insert("""
            INSERT INTO dbo.cat_payment_method (
                method_code, method_name, method_type, is_electronic,
                included_in_revenue, needs_external_ref, status
            )
            OUTPUT INSERTED.id
            VALUES (#{code}, #{name}, #{type}, #{electronic},
                    #{includedInRevenue}, #{needsExternalReference}, #{status})
            """)
    long insertMethod(PaymentMethodDraft draft);

    @Select("""
            SELECT COALESCE(MAX(sort_no), 0) + 10
            FROM dbo.cat_payment_method_store
            WHERE store_id = #{storeId}
            """)
    int nextSortNo(long storeId);

    @Insert("""
            INSERT INTO dbo.cat_payment_method_store (
                payment_method_id, store_id, sort_no, enabled
            ) VALUES (#{paymentMethodId}, #{storeId}, #{sortNo}, #{enabled})
            """)
    int insertStore(
            @Param("paymentMethodId") long paymentMethodId,
            @Param("storeId") long storeId,
            @Param("sortNo") int sortNo,
            @Param("enabled") boolean enabled);

    @Update("""
            UPDATE dbo.cat_payment_method
            SET method_name = #{name}, method_type = #{type}, is_electronic = #{electronic},
                included_in_revenue = #{includedInRevenue},
                needs_external_ref = #{needsExternalReference}, status = #{status},
                updated_at = sysdatetime()
            WHERE id = #{id} AND row_version = CONVERT(varbinary(8), #{version}, 1)
            """)
    int updateMethod(PaymentMethodUpdate update);

    @Update("""
            UPDATE dbo.cat_payment_method_store
            SET enabled = #{enabled}, sort_no = #{sortNo}
            WHERE payment_method_id = #{paymentMethodId} AND store_id = #{storeId}
              AND row_version = CONVERT(varbinary(8), #{version}, 1)
            """)
    int updateStore(PaymentMethodStoreUpdate update);

    @Update("""
            UPDATE dbo.cat_payment_method_store
            SET sort_no = #{update.sortNo}
            WHERE payment_method_id = #{update.paymentMethodId} AND store_id = #{storeId}
              AND row_version = CONVERT(varbinary(8), #{update.version}, 1)
            """)
    int updateSort(@Param("storeId") long storeId, @Param("update") PaymentMethodSortUpdate update);

    @Delete("""
            DELETE FROM dbo.cat_payment_method_store
            WHERE payment_method_id = #{paymentMethodId} AND store_id = #{storeId}
              AND row_version = CONVERT(varbinary(8), #{version}, 1)
            """)
    int deleteStore(PaymentMethodStoreUpdate update);
}
