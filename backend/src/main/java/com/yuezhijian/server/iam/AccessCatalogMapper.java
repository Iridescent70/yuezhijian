package com.yuezhijian.server.iam;

import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AccessCatalogMapper {
    @Select("""
            SELECT id, store_code AS code, store_name AS name,
                   COALESCE(store_level, 'STANDARD') AS level, status
            FROM dbo.org_store
            WHERE status = 'ACTIVE'
            ORDER BY id
            """)
    List<StoreSummary> findStores();

    @Select("""
            SELECT id, role_code AS code, role_name AS name,
                   data_scope_type AS dataScope, status
            FROM dbo.iam_role
            ORDER BY id
            """)
    List<RoleRow> findRoles();

    @Select("""
            SELECT p.permission_code
            FROM dbo.iam_role_permission rp
            JOIN dbo.iam_permission p ON p.id = rp.permission_id
            WHERE rp.role_id = #{roleId} AND rp.effect = 'ALLOW'
            ORDER BY p.permission_code
            """)
    List<String> findPermissionsByRoleId(long roleId);

    @Select("SELECT permission_code FROM dbo.iam_permission ORDER BY permission_code")
    List<String> findAllPermissionCodes();

    @Select("""
            SELECT id, parent_id AS parentId, menu_code AS code, name, route, icon,
                   sort_no AS sortNo, permission_code AS permission
            FROM dbo.iam_menu
            WHERE status = 'ACTIVE' AND client_type = 'PC'
            ORDER BY parent_id, sort_no, id
            """)
    List<MenuRow> findMenus();

    @Select("""
            SELECT u.id, u.username, u.password_hash AS passwordHash, u.full_name AS fullName,
                   u.status, u.locked_at AS lockedAt, e.primary_store_id AS currentStoreId
            FROM dbo.iam_user u
            LEFT JOIN dbo.org_employee e ON e.id = u.employee_id
            WHERE u.username = #{username}
            """)
    AccessUserAccount findUserByUsername(String username);

    @Select("""
            SELECT r.role_code
            FROM dbo.iam_user_role ur
            JOIN dbo.iam_role r ON r.id = ur.role_id
            WHERE ur.user_id = #{userId} AND r.status = 'ACTIVE'
            ORDER BY r.role_code
            """)
    List<String> findRoleCodesByUserId(long userId);

    @Select("""
            SELECT DISTINCT p.permission_code
            FROM dbo.iam_user_role ur
            JOIN dbo.iam_role r ON r.id = ur.role_id AND r.status = 'ACTIVE'
            JOIN dbo.iam_role_permission rp ON rp.role_id = r.id AND rp.effect = 'ALLOW'
            JOIN dbo.iam_permission p ON p.id = rp.permission_id
            WHERE ur.user_id = #{userId}
            ORDER BY p.permission_code
            """)
    List<String> findPermissionCodesByUserId(long userId);

    @Select("SELECT COUNT(1) FROM dbo.iam_user WHERE username = #{username}")
    int countUserByUsername(String username);

    @Insert("""
            INSERT INTO dbo.iam_user (username, password_hash, full_name, is_admin, password_changed_at)
            VALUES (#{username}, #{passwordHash}, #{fullName}, 1, sysdatetime())
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insertBootstrapUser(BootstrapUser user);

    @Insert("""
            INSERT INTO dbo.iam_user_role (user_id, role_id, assigned_by)
            SELECT #{userId}, id, #{userId}
            FROM dbo.iam_role
            WHERE role_code = #{roleCode}
            """)
    int assignRole(@Param("userId") long userId, @Param("roleCode") String roleCode);
}
