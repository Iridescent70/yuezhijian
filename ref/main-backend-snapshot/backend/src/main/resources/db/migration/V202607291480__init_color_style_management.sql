-- 需求：系统管理-22、API-CFG-007、UI-CFG-002
-- 目的：线上试色分层分类、色号、多分类归属、多素材、权限及审计基线。
-- 证据：旧ItemColorStyle/Group/Assign Hibernate映射和管理/WAP JSP；导入模板尚未确认。

CREATE TABLE dbo.cat_color_style_category (
    id bigint IDENTITY(1,1) NOT NULL CONSTRAINT pk_cat_color_style_category PRIMARY KEY,
    parent_id bigint NULL,
    category_code varchar(64) NOT NULL,
    category_name nvarchar(100) NOT NULL,
    image_file_id bigint NULL,
    sort_no int NOT NULL CONSTRAINT df_cat_color_style_category_sort DEFAULT (0),
    status varchar(32) NOT NULL CONSTRAINT df_cat_color_style_category_status DEFAULT ('ACTIVE'),
    created_at datetime2(3) NOT NULL CONSTRAINT df_cat_color_style_category_created DEFAULT (sysdatetime()),
    created_by bigint NOT NULL,
    updated_at datetime2(3) NOT NULL CONSTRAINT df_cat_color_style_category_updated DEFAULT (sysdatetime()),
    updated_by bigint NOT NULL,
    row_version rowversion NOT NULL,
    CONSTRAINT fk_cat_color_style_category_parent FOREIGN KEY (parent_id)
        REFERENCES dbo.cat_color_style_category(id),
    CONSTRAINT fk_cat_color_style_category_image FOREIGN KEY (image_file_id)
        REFERENCES dbo.sys_file_object(id),
    CONSTRAINT fk_cat_color_style_category_creator FOREIGN KEY (created_by) REFERENCES dbo.iam_user(id),
    CONSTRAINT fk_cat_color_style_category_updater FOREIGN KEY (updated_by) REFERENCES dbo.iam_user(id),
    CONSTRAINT uq_cat_color_style_category_code UNIQUE (category_code),
    CONSTRAINT ck_cat_color_style_category_parent CHECK (parent_id IS NULL OR parent_id <> id),
    CONSTRAINT ck_cat_color_style_category_code CHECK (
        LEN(category_code) > 0 AND category_code NOT LIKE '%[^A-Z0-9_-]%'
    ),
    CONSTRAINT ck_cat_color_style_category_name CHECK (LEN(LTRIM(RTRIM(category_name))) > 0),
    CONSTRAINT ck_cat_color_style_category_sort CHECK (sort_no BETWEEN 0 AND 9999),
    CONSTRAINT ck_cat_color_style_category_status CHECK (status IN ('ACTIVE', 'DISABLED'))
);

CREATE INDEX ix_cat_color_style_category_parent
    ON dbo.cat_color_style_category (parent_id, status, sort_no, id);

CREATE TABLE dbo.cat_color_style (
    id bigint IDENTITY(1,1) NOT NULL CONSTRAINT pk_cat_color_style PRIMARY KEY,
    color_code varchar(64) NOT NULL,
    color_name nvarchar(100) NOT NULL,
    description nvarchar(500) NULL,
    sort_no int NOT NULL CONSTRAINT df_cat_color_style_sort DEFAULT (0),
    status varchar(32) NOT NULL CONSTRAINT df_cat_color_style_status DEFAULT ('ACTIVE'),
    created_at datetime2(3) NOT NULL CONSTRAINT df_cat_color_style_created DEFAULT (sysdatetime()),
    created_by bigint NOT NULL,
    updated_at datetime2(3) NOT NULL CONSTRAINT df_cat_color_style_updated DEFAULT (sysdatetime()),
    updated_by bigint NOT NULL,
    row_version rowversion NOT NULL,
    CONSTRAINT fk_cat_color_style_creator FOREIGN KEY (created_by) REFERENCES dbo.iam_user(id),
    CONSTRAINT fk_cat_color_style_updater FOREIGN KEY (updated_by) REFERENCES dbo.iam_user(id),
    CONSTRAINT uq_cat_color_style_code UNIQUE (color_code),
    CONSTRAINT ck_cat_color_style_code CHECK (
        LEN(color_code) > 0 AND color_code NOT LIKE '%[^A-Z0-9_-]%'
    ),
    CONSTRAINT ck_cat_color_style_name CHECK (LEN(LTRIM(RTRIM(color_name))) > 0),
    CONSTRAINT ck_cat_color_style_sort CHECK (sort_no BETWEEN 0 AND 9999),
    CONSTRAINT ck_cat_color_style_status CHECK (status IN ('ACTIVE', 'DISABLED'))
);

CREATE INDEX ix_cat_color_style_status
    ON dbo.cat_color_style (status, sort_no, id)
    INCLUDE (color_code, color_name);

CREATE TABLE dbo.cat_color_style_category_assignment (
    category_id bigint NOT NULL,
    color_style_id bigint NOT NULL,
    created_at datetime2(3) NOT NULL
        CONSTRAINT df_cat_color_style_assignment_created DEFAULT (sysdatetime()),
    created_by bigint NOT NULL,
    CONSTRAINT pk_cat_color_style_assignment PRIMARY KEY (category_id, color_style_id),
    CONSTRAINT fk_cat_color_style_assignment_category FOREIGN KEY (category_id)
        REFERENCES dbo.cat_color_style_category(id),
    CONSTRAINT fk_cat_color_style_assignment_style FOREIGN KEY (color_style_id)
        REFERENCES dbo.cat_color_style(id),
    CONSTRAINT fk_cat_color_style_assignment_creator FOREIGN KEY (created_by)
        REFERENCES dbo.iam_user(id)
);

CREATE INDEX ix_cat_color_style_assignment_style
    ON dbo.cat_color_style_category_assignment (color_style_id, category_id);

CREATE TABLE dbo.cat_color_style_asset (
    id bigint IDENTITY(1,1) NOT NULL CONSTRAINT pk_cat_color_style_asset PRIMARY KEY,
    color_style_id bigint NOT NULL,
    file_id bigint NOT NULL,
    sort_no int NOT NULL CONSTRAINT df_cat_color_style_asset_sort DEFAULT (0),
    status varchar(32) NOT NULL CONSTRAINT df_cat_color_style_asset_status DEFAULT ('ACTIVE'),
    created_at datetime2(3) NOT NULL CONSTRAINT df_cat_color_style_asset_created DEFAULT (sysdatetime()),
    created_by bigint NOT NULL,
    updated_at datetime2(3) NOT NULL CONSTRAINT df_cat_color_style_asset_updated DEFAULT (sysdatetime()),
    updated_by bigint NOT NULL,
    row_version rowversion NOT NULL,
    CONSTRAINT fk_cat_color_style_asset_style FOREIGN KEY (color_style_id)
        REFERENCES dbo.cat_color_style(id),
    CONSTRAINT fk_cat_color_style_asset_file FOREIGN KEY (file_id) REFERENCES dbo.sys_file_object(id),
    CONSTRAINT fk_cat_color_style_asset_creator FOREIGN KEY (created_by) REFERENCES dbo.iam_user(id),
    CONSTRAINT fk_cat_color_style_asset_updater FOREIGN KEY (updated_by) REFERENCES dbo.iam_user(id),
    CONSTRAINT uq_cat_color_style_asset_file UNIQUE (file_id),
    CONSTRAINT ck_cat_color_style_asset_sort CHECK (sort_no BETWEEN 0 AND 9999),
    CONSTRAINT ck_cat_color_style_asset_status CHECK (status IN ('ACTIVE', 'DISABLED'))
);

CREATE INDEX ix_cat_color_style_asset_style
    ON dbo.cat_color_style_asset (color_style_id, status, sort_no, id);

INSERT INTO dbo.iam_permission (permission_code, permission_name, resource_type, api_pattern, http_method)
VALUES
    ('system:color-style:view', N'查看线上试色', 'MENU', '/api/v1/color-style*/**', 'GET'),
    ('system:color-style:manage', N'维护线上试色', 'BUTTON', '/api/v1/color-style*/**', 'POST,PUT');

INSERT INTO dbo.iam_role_permission (role_id, permission_id, effect)
SELECT role.id, permission.id, 'ALLOW'
FROM dbo.iam_role role CROSS JOIN dbo.iam_permission permission
WHERE role.role_code = 'HEADQUARTERS_ADMIN'
  AND permission.permission_code IN ('system:color-style:view', 'system:color-style:manage');

UPDATE dbo.iam_menu SET sort_no = 75
WHERE menu_code = 'audit-logs' AND parent_id = (
    SELECT id FROM dbo.iam_menu WHERE menu_code = 'system'
);

INSERT INTO dbo.iam_menu (
    parent_id, menu_code, name, route, icon, sort_no, client_type, permission_code
)
SELECT id, 'color-styles', N'线上试色', '/app/system/color-styles', 'Brush', 70,
       'PC', 'system:color-style:view'
FROM dbo.iam_menu WHERE menu_code = 'system';

-- 验证：4张业务表、2项权限和1个菜单；分类与素材图片均通过私有文件接口读取。
