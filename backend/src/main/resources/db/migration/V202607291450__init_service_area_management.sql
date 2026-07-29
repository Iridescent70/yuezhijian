-- 需求：系统管理-30、优化系统管理-03、到家服务-02、API-CFG-001/002、UI-CFG-003
-- 目的：建立门店服务小区、范围、上门费、权限、并发版本和操作人证据。
-- 恢复：发布前可删除本Migration对象；共享环境执行后通过新Migration修复，禁止回改历史脚本。

CREATE TABLE dbo.cfg_service_area (
    id bigint IDENTITY(1,1) NOT NULL CONSTRAINT pk_cfg_service_area PRIMARY KEY,
    store_id bigint NOT NULL,
    city nvarchar(64) NOT NULL,
    district nvarchar(64) NOT NULL,
    address nvarchar(300) NOT NULL,
    longitude decimal(10,7) NOT NULL,
    latitude decimal(10,7) NOT NULL,
    radius_km decimal(9,3) NOT NULL,
    visit_fee decimal(19,4) NOT NULL CONSTRAINT df_cfg_service_area_visit_fee DEFAULT (0),
    status varchar(32) NOT NULL CONSTRAINT df_cfg_service_area_status DEFAULT ('ACTIVE'),
    created_at datetime2(3) NOT NULL CONSTRAINT df_cfg_service_area_created_at DEFAULT (sysdatetime()),
    created_by bigint NULL,
    updated_at datetime2(3) NOT NULL CONSTRAINT df_cfg_service_area_updated_at DEFAULT (sysdatetime()),
    updated_by bigint NULL,
    row_version rowversion NOT NULL,
    CONSTRAINT uq_cfg_service_area_store_address UNIQUE (store_id, address),
    CONSTRAINT fk_cfg_service_area_store FOREIGN KEY (store_id) REFERENCES dbo.org_store(id),
    CONSTRAINT fk_cfg_service_area_created_by FOREIGN KEY (created_by) REFERENCES dbo.iam_user(id),
    CONSTRAINT fk_cfg_service_area_updated_by FOREIGN KEY (updated_by) REFERENCES dbo.iam_user(id),
    CONSTRAINT ck_cfg_service_area_longitude CHECK (longitude BETWEEN -180 AND 180),
    CONSTRAINT ck_cfg_service_area_latitude CHECK (latitude BETWEEN -90 AND 90),
    CONSTRAINT ck_cfg_service_area_radius CHECK (radius_km BETWEEN 0.001 AND 200),
    CONSTRAINT ck_cfg_service_area_fee CHECK (visit_fee >= 0),
    CONSTRAINT ck_cfg_service_area_status CHECK (status IN ('ACTIVE', 'DISABLED'))
);

CREATE INDEX ix_cfg_service_area_store_status_region
    ON dbo.cfg_service_area (store_id, status, city, district, id);

INSERT INTO dbo.iam_permission (permission_code, permission_name, resource_type, api_pattern, http_method)
VALUES
    ('home:service-area:view', N'查看服务小区', 'MENU', '/api/v1/service-areas/**', 'GET'),
    ('home:service-area:manage', N'维护服务小区', 'BUTTON', '/api/v1/service-areas/**', 'POST,PUT');

INSERT INTO dbo.iam_role_permission (role_id, permission_id, effect)
SELECT role.id, permission.id, 'ALLOW'
FROM dbo.iam_role role CROSS JOIN dbo.iam_permission permission
WHERE role.role_code IN ('HEADQUARTERS_ADMIN', 'STORE_MANAGER')
  AND permission.permission_code IN ('home:service-area:view', 'home:service-area:manage');

INSERT INTO dbo.iam_menu (
    parent_id, menu_code, name, route, icon, sort_no, client_type, permission_code
)
SELECT id, 'service-areas', N'服务小区', '/app/system/service-areas', 'Location', 67,
       'PC', 'home:service-area:view'
FROM dbo.iam_menu WHERE menu_code = 'system';

-- 验证：新增1张表、2项权限和1个菜单；门店角色只能通过后端数据范围维护本店记录。
