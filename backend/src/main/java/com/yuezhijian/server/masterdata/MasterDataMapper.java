package com.yuezhijian.server.masterdata;

import java.math.BigDecimal;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface MasterDataMapper {
    @Select("""
            <script>
            SELECT id, position_code AS code, position_name AS name, position_level AS level,
                   default_service_rate AS defaultServiceRate, default_sales_rate AS defaultSalesRate,
                   status, CONVERT(varchar(18), row_version, 1) AS version
            FROM dbo.org_position
            <if test="activeOnly">
            WHERE status = 'ACTIVE'
            </if>
            ORDER BY position_level DESC, id
            </script>
            """)
    List<PositionOption> findPositions(@Param("activeOnly") boolean activeOnly);

    @Select("""
            SELECT id, position_code AS code, position_name AS name, position_level AS level,
                   default_service_rate AS defaultServiceRate, default_sales_rate AS defaultSalesRate,
                   status, CONVERT(varchar(18), row_version, 1) AS version
            FROM dbo.org_position
            WHERE id = #{id}
            """)
    PositionOption findPosition(long id);

    @Select("""
            SELECT id, category_code AS code, name, category_type AS type, status
            FROM dbo.cat_category
            WHERE category_type = #{type} AND status = 'ACTIVE'
            ORDER BY sort_no, id
            """)
    List<CategoryOption> findCategories(String type);

    @Select("""
            SELECT id, unit_code AS code, unit_name AS name, decimal_places AS decimalPlaces, status
            FROM dbo.cat_unit
            WHERE status = 'ACTIVE'
            ORDER BY id
            """)
    List<UnitOption> findUnits();

    @Select("""
            <script>
            SELECT e.id, e.employee_no AS employeeNo, e.name,
                   CASE WHEN e.mobile_last4 IS NULL THEN NULL
                        ELSE CONCAT('*******', RTRIM(e.mobile_last4)) END AS maskedMobile,
                   e.position_id AS positionId, p.position_name AS positionName,
                   e.primary_store_id AS storeId, s.store_name AS storeName,
                   e.hire_date AS hireDate, e.leave_date AS leaveDate,
                   e.can_service AS canService, e.can_sell AS canSell, e.status,
                   CONVERT(varchar(18), e.row_version, 1) AS version
            FROM dbo.org_employee e
            LEFT JOIN dbo.org_position p ON p.id = e.position_id
            LEFT JOIN dbo.org_store s ON s.id = e.primary_store_id
            WHERE 1 = 1
            <if test="storeId != null">
              AND e.primary_store_id = #{storeId}
            </if>
            <if test="keyword != null">
              AND (e.employee_no LIKE CONCAT('%', #{keyword}, '%')
                   OR e.name LIKE CONCAT('%', #{keyword}, '%'))
            </if>
            ORDER BY e.id DESC
            </script>
            """)
    List<EmployeeSummary> findEmployees(@Param("storeId") Long storeId, @Param("keyword") String keyword);

    @Select("""
            SELECT e.id, e.employee_no AS employeeNo, e.name,
                   CASE WHEN e.mobile_last4 IS NULL THEN NULL
                        ELSE CONCAT('*******', RTRIM(e.mobile_last4)) END AS maskedMobile,
                   e.position_id AS positionId, p.position_name AS positionName,
                   e.primary_store_id AS storeId, s.store_name AS storeName,
                   e.hire_date AS hireDate, e.leave_date AS leaveDate,
                   e.can_service AS canService, e.can_sell AS canSell, e.status,
                   CONVERT(varchar(18), e.row_version, 1) AS version
            FROM dbo.org_employee e
            LEFT JOIN dbo.org_position p ON p.id = e.position_id
            LEFT JOIN dbo.org_store s ON s.id = e.primary_store_id
            WHERE e.id = #{id}
            """)
    EmployeeSummary findEmployee(long id);

    @Select("""
            <script>
            SELECT w.id, w.store_id AS storeId, s.store_name AS storeName,
                   w.workstation_code AS code, w.name, w.capacity, w.sort_no AS sortNo, w.status,
                   CONVERT(varchar(18), w.row_version, 1) AS version
            FROM dbo.org_workstation w
            JOIN dbo.org_store s ON s.id = w.store_id
            WHERE 1 = 1
            <if test="storeId != null">
              AND w.store_id = #{storeId}
            </if>
            ORDER BY s.store_code, w.sort_no, w.id
            </script>
            """)
    List<WorkstationSummary> findWorkstations(@Param("storeId") Long storeId);

    @Select("""
            SELECT w.id, w.store_id AS storeId, s.store_name AS storeName,
                   w.workstation_code AS code, w.name, w.capacity, w.sort_no AS sortNo, w.status,
                   CONVERT(varchar(18), w.row_version, 1) AS version
            FROM dbo.org_workstation w
            JOIN dbo.org_store s ON s.id = w.store_id
            WHERE w.id = #{id}
            """)
    WorkstationSummary findWorkstation(long id);

    @Select("""
            <script>
            SELECT service.id, service.service_code AS code, service.service_name AS name,
                   service.category_id AS categoryId, category.name AS categoryName,
                   service.duration_minutes AS durationMinutes, service.cost_amount AS costAmount,
                   service.list_price AS listPrice,
                   COALESCE(store_cfg.sale_price, service.list_price) AS storePrice,
                   COALESCE(store_cfg.sale_status, 'OFF_SALE') AS saleStatus, service.status
            FROM dbo.cat_service service
            JOIN dbo.cat_category category ON category.id = service.category_id
            OUTER APPLY (
                SELECT TOP 1 item_store.store_id, item_store.sale_price, item_store.sale_status
                FROM dbo.cat_item_store item_store
                WHERE item_store.item_type = 'SERVICE'
                  AND item_store.item_id = service.id
                  <if test="storeId != null">
                    AND item_store.store_id = #{storeId}
                  </if>
                ORDER BY item_store.store_id
            ) store_cfg
            WHERE 1 = 1
            <if test="storeId != null">
              AND store_cfg.store_id IS NOT NULL
            </if>
            <if test="keyword != null">
              AND (service.service_code LIKE CONCAT('%', #{keyword}, '%')
                   OR service.service_name LIKE CONCAT('%', #{keyword}, '%'))
            </if>
            ORDER BY service.id DESC
            </script>
            """)
    List<ServiceItemSummary> findServices(@Param("storeId") Long storeId, @Param("keyword") String keyword);

    @Select("""
            SELECT service.id, service.service_code AS code, service.service_name AS name,
                   service.category_id AS categoryId, category.name AS categoryName,
                   service.duration_minutes AS durationMinutes, service.cost_amount AS costAmount,
                   service.list_price AS listPrice, service.description, service.status,
                   CONVERT(varchar(18), service.row_version, 1) AS version
            FROM dbo.cat_service service
            JOIN dbo.cat_category category ON category.id = service.category_id
            WHERE service.id = #{id}
            """)
    ServiceItemRow findService(long id);

    @Select("""
            SELECT service.id, service.service_code AS code, service.service_name AS name,
                   service.category_id AS categoryId, category.name AS categoryName,
                   service.duration_minutes AS durationMinutes, service.cost_amount AS costAmount,
                   service.list_price AS listPrice, service.description, service.status,
                   CONVERT(varchar(18), service.row_version, 1) AS version
            FROM dbo.cat_service service
            JOIN dbo.cat_category category ON category.id = service.category_id
            WHERE service.service_code = #{code}
            """)
    ServiceItemRow findServiceByCode(String code);

    @Select("""
            SELECT item_store.store_id AS storeId, store.store_name AS storeName,
                   item_store.sale_price AS storePrice, item_store.sale_status AS saleStatus
            FROM dbo.cat_item_store item_store
            JOIN dbo.org_store store ON store.id = item_store.store_id
            WHERE item_store.item_type = 'SERVICE' AND item_store.item_id = #{serviceId}
            ORDER BY store.store_code, item_store.store_id
            """)
    List<ServiceStoreConfig> findServiceStores(long serviceId);

    @Select(value = """
            INSERT INTO dbo.org_position (
                position_code, position_name, position_level,
                default_service_rate, default_sales_rate, created_by, updated_by
            )
            OUTPUT INSERTED.id
            VALUES (
                #{code}, #{name}, #{level}, #{defaultServiceRate}, #{defaultSalesRate},
                #{createdBy}, #{createdBy}
            )
            """, affectData = true)
    long insertPosition(NewPosition position);

    @Update("""
            UPDATE dbo.org_position
            SET position_name = #{name}, position_level = #{level},
                default_service_rate = #{defaultServiceRate}, default_sales_rate = #{defaultSalesRate},
                status = #{status}, updated_at = sysdatetime(), updated_by = #{updatedBy}
            WHERE id = #{id} AND row_version = CONVERT(binary(8), #{version}, 1)
            """)
    int updatePosition(PositionUpdate update);

    @Select(value = """
            INSERT INTO dbo.org_employee (
                employee_no, name, mobile_ciphertext, mobile_hash, mobile_last4,
                position_id, primary_store_id, hire_date, can_service, can_sell, created_by, updated_by
            )
            OUTPUT INSERTED.id
            VALUES (
                #{employeeNo}, #{name}, #{mobileCiphertext}, #{mobileHash}, #{mobileLast4},
                #{positionId}, #{primaryStoreId}, #{hireDate}, #{canService}, #{canSell}, #{createdBy}, #{createdBy}
            )
            """, affectData = true)
    long insertEmployee(ProtectedEmployeeRow employee);

    @Update("""
            <script>
            UPDATE dbo.org_employee
            SET name = #{name},
                <if test="mobileChanged">
                mobile_ciphertext = #{mobileCiphertext}, mobile_hash = #{mobileHash}, mobile_last4 = #{mobileLast4},
                </if>
                position_id = #{positionId}, primary_store_id = #{primaryStoreId},
                hire_date = #{hireDate}, leave_date = #{leaveDate},
                can_service = #{canService}, can_sell = #{canSell}, status = #{status},
                updated_at = sysdatetime(), updated_by = #{updatedBy}
            WHERE id = #{id} AND row_version = CONVERT(binary(8), #{version}, 1)
            </script>
            """)
    int updateEmployee(ProtectedEmployeeUpdate update);

    @Select(value = """
            INSERT INTO dbo.org_workstation (
                store_id, workstation_code, name, capacity, sort_no, created_by, updated_by
            )
            OUTPUT INSERTED.id
            VALUES (#{storeId}, #{code}, #{name}, #{capacity}, #{sortNo}, #{createdBy}, #{createdBy})
            """, affectData = true)
    long insertWorkstation(NewWorkstation workstation);

    @Update("""
            UPDATE dbo.org_workstation
            SET name = #{name}, capacity = #{capacity}, sort_no = #{sortNo}, status = #{status},
                updated_at = sysdatetime(), updated_by = #{updatedBy}
            WHERE id = #{id} AND row_version = CONVERT(binary(8), #{version}, 1)
            """)
    int updateWorkstation(WorkstationUpdate update);

    @Select(value = """
            INSERT INTO dbo.cat_service (
                service_code, service_name, category_id, duration_minutes,
                cost_amount, list_price, description, created_by, updated_by
            )
            OUTPUT INSERTED.id
            VALUES (
                #{code}, #{name}, #{categoryId}, #{durationMinutes},
                #{costAmount}, #{listPrice}, #{description}, #{createdBy}, #{createdBy}
            )
            """, affectData = true)
    long insertService(NewServiceItem service);

    @Insert("""
            INSERT INTO dbo.cat_item_store (
                item_type, item_id, store_id, sale_price, created_by, updated_by
            ) VALUES ('SERVICE', #{serviceId}, #{storeId}, #{storePrice}, #{createdBy}, #{createdBy})
            """)
    void insertServiceStore(
            @Param("serviceId") long serviceId,
            @Param("storeId") long storeId,
            @Param("storePrice") BigDecimal storePrice,
            @Param("createdBy") long createdBy);

    @Update("""
            UPDATE dbo.cat_service
            SET service_name = #{name}, category_id = #{categoryId},
                duration_minutes = #{durationMinutes}, cost_amount = #{costAmount},
                list_price = #{listPrice}, description = #{description}, status = #{status},
                updated_at = sysdatetime(), updated_by = #{updatedBy}
            WHERE id = #{id} AND row_version = CONVERT(binary(8), #{version}, 1)
            """)
    int updateService(ServiceItemUpdate update);

    @Update("""
            UPDATE dbo.cat_item_store
            SET sale_price = #{update.storePrice}, sale_status = #{update.saleStatus},
                updated_at = sysdatetime(), updated_by = #{update.updatedBy}
            WHERE item_type = 'SERVICE' AND item_id = #{update.id} AND store_id = #{update.storeId}
            """)
    int updateServiceStore(@Param("update") ServiceItemUpdate update);
}
