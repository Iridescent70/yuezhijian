-- 需求：系统管理-32、API-CFG-006、UI-CFG-001
-- 目的：首页图片文件、展示位置、跳转、排序、有效期、状态、权限和审计基线。
-- 恢复：共享环境执行后只通过更高版本Migration修复；停用配置，不删除已引用文件。

CREATE TABLE dbo.cfg_banner (
    id bigint IDENTITY(1,1) NOT NULL CONSTRAINT pk_cfg_banner PRIMARY KEY,
    position_code varchar(64) NOT NULL,
    title nvarchar(200) NOT NULL,
    image_file_id bigint NOT NULL,
    link_type varchar(32) NOT NULL CONSTRAINT df_cfg_banner_link_type DEFAULT ('NONE'),
    link_value nvarchar(500) NULL,
    sort_no int NOT NULL CONSTRAINT df_cfg_banner_sort DEFAULT (0),
    valid_from datetime2(3) NULL,
    valid_to datetime2(3) NULL,
    status varchar(32) NOT NULL CONSTRAINT df_cfg_banner_status DEFAULT ('ACTIVE'),
    created_at datetime2(3) NOT NULL CONSTRAINT df_cfg_banner_created DEFAULT (sysdatetime()),
    created_by bigint NOT NULL,
    updated_at datetime2(3) NOT NULL CONSTRAINT df_cfg_banner_updated DEFAULT (sysdatetime()),
    updated_by bigint NOT NULL,
    row_version rowversion NOT NULL,
    CONSTRAINT fk_cfg_banner_image FOREIGN KEY (image_file_id) REFERENCES dbo.sys_file_object(id),
    CONSTRAINT fk_cfg_banner_creator FOREIGN KEY (created_by) REFERENCES dbo.iam_user(id),
    CONSTRAINT fk_cfg_banner_updater FOREIGN KEY (updated_by) REFERENCES dbo.iam_user(id),
    CONSTRAINT ck_cfg_banner_position CHECK (position_code IN ('PC_HOME', 'HOME_SERVICE_HOME')),
    CONSTRAINT ck_cfg_banner_title CHECK (LEN(LTRIM(RTRIM(title))) > 0),
    CONSTRAINT ck_cfg_banner_link_type CHECK (link_type IN ('NONE', 'INTERNAL', 'EXTERNAL')),
    CONSTRAINT ck_cfg_banner_link_value CHECK (
        (link_type = 'NONE' AND link_value IS NULL)
        OR (link_type IN ('INTERNAL', 'EXTERNAL') AND LEN(LTRIM(RTRIM(link_value))) > 0)
    ),
    CONSTRAINT ck_cfg_banner_sort CHECK (sort_no BETWEEN 0 AND 9999),
    CONSTRAINT ck_cfg_banner_validity CHECK (
        valid_from IS NULL OR valid_to IS NULL OR valid_to > valid_from
    ),
    CONSTRAINT ck_cfg_banner_status CHECK (status IN ('ACTIVE', 'DISABLED'))
);

CREATE INDEX ix_cfg_banner_active
    ON dbo.cfg_banner (position_code, status, sort_no, id)
    INCLUDE (title, image_file_id, link_type, link_value, valid_from, valid_to);

INSERT INTO dbo.iam_permission (permission_code, permission_name, resource_type, api_pattern, http_method)
VALUES
    ('system:banner:view', N'查看首页图片', 'MENU', '/api/v1/banners/**', 'GET'),
    ('system:banner:manage', N'维护首页图片', 'BUTTON', '/api/v1/banners/**', 'POST,PUT');

INSERT INTO dbo.iam_role_permission (role_id, permission_id, effect)
SELECT role.id, permission.id, 'ALLOW'
FROM dbo.iam_role role CROSS JOIN dbo.iam_permission permission
WHERE role.role_code = 'HEADQUARTERS_ADMIN'
  AND permission.permission_code IN ('system:banner:view', 'system:banner:manage');

INSERT INTO dbo.iam_menu (
    parent_id, menu_code, name, route, icon, sort_no, client_type, permission_code
)
SELECT id, 'banners', N'首页图片', '/app/system/banners', 'Picture', 69,
       'PC', 'system:banner:view'
FROM dbo.iam_menu WHERE menu_code = 'system';

-- 验证：新增1张配置表、2项权限和1个菜单；图片正文只保存在私有对象存储。
