package com.yuezhijian.server.servicearea;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ServiceAreaMapper {
    String AREA_SELECT = """
            SELECT area.id, area.store_id AS storeId, store.store_code AS storeCode,
                   store.store_name AS storeName, area.city, area.district, area.address,
                   area.longitude, area.latitude, area.radius_km AS radiusKm,
                   area.visit_fee AS visitFee, area.status, area.updated_at AS updatedAt,
                   area.updated_by AS updatedBy, COALESCE(operator.full_name, N'系统任务') AS updatedByName,
                   CONVERT(varchar(18), area.row_version, 1) AS version
            FROM dbo.cfg_service_area area
            JOIN dbo.org_store store ON store.id = area.store_id
            LEFT JOIN dbo.iam_user operator ON operator.id = area.updated_by
            """;

    @Select("""
            <script>
            """ + AREA_SELECT + """
            WHERE 1 = 1
            <if test="storeId != null">
              AND area.store_id = #{storeId}
            </if>
            <if test="keyword != null">
              AND (area.city LIKE CONCAT('%', #{keyword}, '%')
                   OR area.district LIKE CONCAT('%', #{keyword}, '%')
                   OR area.address LIKE CONCAT('%', #{keyword}, '%'))
            </if>
            <if test="status != null">
              AND area.status = #{status}
            </if>
            ORDER BY store.store_code,
                     CASE WHEN area.status = 'ACTIVE' THEN 0 ELSE 1 END,
                     area.city, area.district, area.id
            </script>
            """)
    List<ServiceArea> findAll(
            @Param("storeId") Long storeId,
            @Param("keyword") String keyword,
            @Param("status") String status);

    @Select(AREA_SELECT + " WHERE area.id = #{id}")
    ServiceArea find(long id);

    @Select(value = """
            INSERT INTO dbo.cfg_service_area (
                store_id, city, district, address, longitude, latitude,
                radius_km, visit_fee, created_by, updated_by
            )
            OUTPUT INSERTED.id
            VALUES (
                #{storeId}, #{city}, #{district}, #{address}, #{longitude}, #{latitude},
                #{radiusKm}, #{visitFee}, #{operatorId}, #{operatorId}
            )
            """, affectData = true)
    long insert(NewServiceArea area);

    @Update("""
            UPDATE dbo.cfg_service_area
            SET city = #{city}, district = #{district}, address = #{address},
                longitude = #{longitude}, latitude = #{latitude}, radius_km = #{radiusKm},
                visit_fee = #{visitFee}, status = #{status},
                updated_at = sysdatetime(), updated_by = #{operatorId}
            WHERE id = #{id} AND row_version = CONVERT(binary(8), #{version}, 1)
            """)
    int update(ServiceAreaUpdate update);
}
