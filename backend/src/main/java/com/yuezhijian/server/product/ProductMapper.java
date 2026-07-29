package com.yuezhijian.server.product;

import java.math.BigDecimal;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ProductMapper {
    String ROW_SELECT = """
            SELECT product.id, product.product_code AS code, product.product_name AS name,
                   product.category_id AS categoryId, category.name AS categoryName,
                   product.unit_id AS unitId, unit.unit_name AS unitName, product.barcode,
                   product.cost_price AS costPrice, product.sale_price AS salePrice,
                   product.track_stock AS trackStock, product.description, product.status,
                   CONVERT(varchar(18), product.row_version, 1) AS version
            FROM dbo.cat_product product
            JOIN dbo.cat_category category ON category.id = product.category_id
            JOIN dbo.cat_unit unit ON unit.id = product.unit_id
            """;

    @Select("""
            <script>
            SELECT product.id, product.product_code AS code, product.product_name AS name,
                   product.category_id AS categoryId, category.name AS categoryName,
                   product.unit_id AS unitId, unit.unit_name AS unitName, product.barcode,
                   product.cost_price AS costPrice, product.sale_price AS salePrice,
                   COALESCE(store_cfg.sale_price, product.sale_price) AS storePrice,
                   product.track_stock AS trackStock,
                   COALESCE(store_cfg.sale_status, 'OFF_SALE') AS saleStatus, product.status
            FROM dbo.cat_product product
            JOIN dbo.cat_category category ON category.id = product.category_id
            JOIN dbo.cat_unit unit ON unit.id = product.unit_id
            OUTER APPLY (
                SELECT TOP 1 cfg.store_id, cfg.sale_price, cfg.sale_status
                FROM dbo.cat_item_store cfg
                WHERE cfg.item_type = 'PRODUCT' AND cfg.item_id = product.id
                <if test="storeId != null">AND cfg.store_id = #{storeId}</if>
                ORDER BY cfg.store_id
            ) store_cfg
            WHERE 1 = 1
            <if test="storeId != null">AND store_cfg.store_id IS NOT NULL</if>
            <if test="categoryId != null">AND product.category_id = #{categoryId}</if>
            <if test="saleStatus != null">AND store_cfg.sale_status = #{saleStatus}</if>
            <if test="keyword != null">
              AND (product.product_code LIKE CONCAT('%', #{keyword}, '%')
                   OR product.product_name LIKE CONCAT('%', #{keyword}, '%')
                   OR product.barcode LIKE CONCAT('%', #{keyword}, '%'))
            </if>
            ORDER BY product.id DESC
            </script>
            """)
    List<ProductSummary> findProducts(
            @Param("storeId") Long storeId,
            @Param("categoryId") Long categoryId,
            @Param("saleStatus") String saleStatus,
            @Param("keyword") String keyword);

    @Select(ROW_SELECT + " WHERE product.id = #{id}")
    ProductRow find(long id);

    @Select(ROW_SELECT + " WHERE product.product_code = #{code}")
    ProductRow findByCode(String code);

    @Select("""
            SELECT cfg.store_id AS storeId, store.store_name AS storeName,
                   cfg.sale_price AS storePrice, cfg.sale_status AS saleStatus
            FROM dbo.cat_item_store cfg
            JOIN dbo.org_store store ON store.id = cfg.store_id
            WHERE cfg.item_type = 'PRODUCT' AND cfg.item_id = #{productId}
            ORDER BY store.store_code, cfg.store_id
            """)
    List<ProductStoreConfig> findStores(long productId);

    @Select(value = """
            INSERT INTO dbo.cat_product (
                product_code, product_name, category_id, unit_id, barcode,
                cost_price, sale_price, track_stock, description, created_by, updated_by
            )
            OUTPUT INSERTED.id
            VALUES (
                #{code}, #{name}, #{categoryId}, #{unitId}, #{barcode},
                #{costPrice}, #{salePrice}, #{trackStock}, #{description}, #{createdBy}, #{createdBy}
            )
            """, affectData = true)
    long insert(NewProduct product);

    @Insert("""
            INSERT INTO dbo.cat_item_store (
                item_type, item_id, store_id, sale_price, created_by, updated_by
            ) VALUES ('PRODUCT', #{productId}, #{storeId}, #{storePrice}, #{operatorId}, #{operatorId})
            """)
    void insertStore(
            @Param("productId") long productId,
            @Param("storeId") long storeId,
            @Param("storePrice") BigDecimal storePrice,
            @Param("operatorId") long operatorId);

    @Update("""
            UPDATE dbo.cat_product
            SET product_name = #{name}, category_id = #{categoryId}, unit_id = #{unitId},
                barcode = #{barcode}, cost_price = #{costPrice}, sale_price = #{salePrice},
                track_stock = #{trackStock}, description = #{description}, status = #{status},
                updated_at = sysdatetime(), updated_by = #{updatedBy}
            WHERE id = #{id} AND row_version = CONVERT(binary(8), #{version}, 1)
            """)
    int update(ProductUpdate update);

    @Update("""
            UPDATE dbo.cat_item_store
            SET sale_price = #{update.storePrice}, sale_status = #{update.saleStatus},
                updated_at = sysdatetime(), updated_by = #{update.updatedBy}
            WHERE item_type = 'PRODUCT' AND item_id = #{update.id} AND store_id = #{update.storeId}
            """)
    int updateStore(@Param("update") ProductUpdate update);
}
