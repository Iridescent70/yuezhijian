-- 需求：ITER-00、系统管理/组织权限基线
-- 目的：建立组织、门店、用户、角色、权限、菜单与 Spring Session 表。
-- 密码：不在 Migration 中写入默认账号或明文密码；管理员由部署初始化流程创建。
-- 恢复：上线前备份；失败时恢复数据库。

CREATE TABLE dbo.org_organization (
    id bigint IDENTITY(1,1) NOT NULL CONSTRAINT pk_org_organization PRIMARY KEY,
    parent_id bigint NULL,
    org_code varchar(64) NOT NULL,
    org_name nvarchar(150) NOT NULL,
    org_type varchar(32) NOT NULL,
    path varchar(1000) NOT NULL,
    sort_no int NOT NULL CONSTRAINT df_org_organization_sort DEFAULT (0),
    status varchar(32) NOT NULL CONSTRAINT df_org_organization_status DEFAULT ('ACTIVE'),
    created_at datetime2(3) NOT NULL CONSTRAINT df_org_organization_created_at DEFAULT (sysdatetime()),
    created_by bigint NULL,
    updated_at datetime2(3) NOT NULL CONSTRAINT df_org_organization_updated_at DEFAULT (sysdatetime()),
    updated_by bigint NULL,
    row_version rowversion NOT NULL,
    CONSTRAINT uq_org_organization_code UNIQUE (org_code),
    CONSTRAINT fk_org_organization_parent FOREIGN KEY (parent_id) REFERENCES dbo.org_organization(id),
    CONSTRAINT ck_org_organization_type CHECK (org_type IN ('COMPANY', 'REGION', 'STORE')),
    CONSTRAINT ck_org_organization_status CHECK (status IN ('ACTIVE', 'DISABLED'))
);

CREATE INDEX ix_org_organization_parent_sort
    ON dbo.org_organization (parent_id, sort_no);

CREATE TABLE dbo.org_store (
    id bigint IDENTITY(1,1) NOT NULL CONSTRAINT pk_org_store PRIMARY KEY,
    organization_id bigint NOT NULL,
    store_code varchar(64) NOT NULL,
    store_name nvarchar(150) NOT NULL,
    store_level varchar(32) NULL,
    phone varchar(32) NULL,
    province nvarchar(64) NULL,
    city nvarchar(64) NULL,
    district nvarchar(64) NULL,
    address nvarchar(300) NULL,
    longitude decimal(10,7) NULL,
    latitude decimal(10,7) NULL,
    business_hours_json nvarchar(max) NULL,
    status varchar(32) NOT NULL CONSTRAINT df_org_store_status DEFAULT ('ACTIVE'),
    created_at datetime2(3) NOT NULL CONSTRAINT df_org_store_created_at DEFAULT (sysdatetime()),
    created_by bigint NULL,
    updated_at datetime2(3) NOT NULL CONSTRAINT df_org_store_updated_at DEFAULT (sysdatetime()),
    updated_by bigint NULL,
    row_version rowversion NOT NULL,
    CONSTRAINT uq_org_store_org UNIQUE (organization_id),
    CONSTRAINT uq_org_store_code UNIQUE (store_code),
    CONSTRAINT fk_org_store_org FOREIGN KEY (organization_id) REFERENCES dbo.org_organization(id),
    CONSTRAINT ck_org_store_status CHECK (status IN ('ACTIVE', 'DISABLED')),
    CONSTRAINT ck_org_store_longitude CHECK (longitude IS NULL OR longitude BETWEEN -180 AND 180),
    CONSTRAINT ck_org_store_latitude CHECK (latitude IS NULL OR latitude BETWEEN -90 AND 90)
);

CREATE TABLE dbo.org_position (
    id bigint IDENTITY(1,1) NOT NULL CONSTRAINT pk_org_position PRIMARY KEY,
    position_code varchar(64) NOT NULL,
    position_name nvarchar(100) NOT NULL,
    position_level int NOT NULL CONSTRAINT df_org_position_level DEFAULT (0),
    default_service_rate decimal(9,6) NOT NULL CONSTRAINT df_org_position_service_rate DEFAULT (0),
    default_sales_rate decimal(9,6) NOT NULL CONSTRAINT df_org_position_sales_rate DEFAULT (0),
    status varchar(32) NOT NULL CONSTRAINT df_org_position_status DEFAULT ('ACTIVE'),
    created_at datetime2(3) NOT NULL CONSTRAINT df_org_position_created_at DEFAULT (sysdatetime()),
    created_by bigint NULL,
    updated_at datetime2(3) NOT NULL CONSTRAINT df_org_position_updated_at DEFAULT (sysdatetime()),
    updated_by bigint NULL,
    row_version rowversion NOT NULL,
    CONSTRAINT uq_org_position_code UNIQUE (position_code),
    CONSTRAINT ck_org_position_rate CHECK (
        default_service_rate BETWEEN 0 AND 1 AND default_sales_rate BETWEEN 0 AND 1
    )
);

CREATE TABLE dbo.org_employee (
    id bigint IDENTITY(1,1) NOT NULL CONSTRAINT pk_org_employee PRIMARY KEY,
    employee_no varchar(64) NOT NULL,
    name nvarchar(100) NOT NULL,
    mobile_ciphertext nvarchar(500) NULL,
    mobile_hash char(64) NULL,
    mobile_last4 char(4) NULL,
    position_id bigint NULL,
    primary_store_id bigint NULL,
    hire_date date NULL,
    leave_date date NULL,
    can_service bit NOT NULL CONSTRAINT df_org_employee_can_service DEFAULT (1),
    can_sell bit NOT NULL CONSTRAINT df_org_employee_can_sell DEFAULT (1),
    status varchar(32) NOT NULL CONSTRAINT df_org_employee_status DEFAULT ('ACTIVE'),
    created_at datetime2(3) NOT NULL CONSTRAINT df_org_employee_created_at DEFAULT (sysdatetime()),
    created_by bigint NULL,
    updated_at datetime2(3) NOT NULL CONSTRAINT df_org_employee_updated_at DEFAULT (sysdatetime()),
    updated_by bigint NULL,
    row_version rowversion NOT NULL,
    CONSTRAINT uq_org_employee_no UNIQUE (employee_no),
    CONSTRAINT fk_org_employee_position FOREIGN KEY (position_id) REFERENCES dbo.org_position(id),
    CONSTRAINT fk_org_employee_store FOREIGN KEY (primary_store_id) REFERENCES dbo.org_store(id),
    CONSTRAINT ck_org_employee_dates CHECK (leave_date IS NULL OR hire_date IS NULL OR leave_date >= hire_date),
    CONSTRAINT ck_org_employee_status CHECK (status IN ('ACTIVE', 'DISABLED', 'LEFT'))
);

CREATE INDEX ix_org_employee_mobile_hash
    ON dbo.org_employee (mobile_hash) WHERE mobile_hash IS NOT NULL;
CREATE INDEX ix_org_employee_store_status
    ON dbo.org_employee (primary_store_id, status);

CREATE TABLE dbo.iam_user (
    id bigint IDENTITY(1,1) NOT NULL CONSTRAINT pk_iam_user PRIMARY KEY,
    username varchar(64) NOT NULL,
    password_hash varchar(255) NOT NULL,
    full_name nvarchar(100) NOT NULL,
    employee_id bigint NULL,
    is_admin bit NOT NULL CONSTRAINT df_iam_user_is_admin DEFAULT (0),
    locked_at datetime2(3) NULL,
    last_login_at datetime2(3) NULL,
    password_changed_at datetime2(3) NULL,
    status varchar(32) NOT NULL CONSTRAINT df_iam_user_status DEFAULT ('ACTIVE'),
    created_at datetime2(3) NOT NULL CONSTRAINT df_iam_user_created_at DEFAULT (sysdatetime()),
    created_by bigint NULL,
    updated_at datetime2(3) NOT NULL CONSTRAINT df_iam_user_updated_at DEFAULT (sysdatetime()),
    updated_by bigint NULL,
    row_version rowversion NOT NULL,
    CONSTRAINT uq_iam_user_username UNIQUE (username),
    CONSTRAINT uq_iam_user_employee UNIQUE (employee_id),
    CONSTRAINT fk_iam_user_employee FOREIGN KEY (employee_id) REFERENCES dbo.org_employee(id),
    CONSTRAINT ck_iam_user_status CHECK (status IN ('ACTIVE', 'DISABLED'))
);

CREATE TABLE dbo.iam_role (
    id bigint IDENTITY(1,1) NOT NULL CONSTRAINT pk_iam_role PRIMARY KEY,
    role_code varchar(64) NOT NULL,
    role_name nvarchar(100) NOT NULL,
    data_scope_type varchar(32) NOT NULL,
    description nvarchar(500) NULL,
    status varchar(32) NOT NULL CONSTRAINT df_iam_role_status DEFAULT ('ACTIVE'),
    created_at datetime2(3) NOT NULL CONSTRAINT df_iam_role_created_at DEFAULT (sysdatetime()),
    created_by bigint NULL,
    updated_at datetime2(3) NOT NULL CONSTRAINT df_iam_role_updated_at DEFAULT (sysdatetime()),
    updated_by bigint NULL,
    row_version rowversion NOT NULL,
    CONSTRAINT uq_iam_role_code UNIQUE (role_code),
    CONSTRAINT ck_iam_role_scope CHECK (data_scope_type IN ('ALL', 'ORGANIZATION', 'STORE', 'CUSTOM', 'SELF')),
    CONSTRAINT ck_iam_role_status CHECK (status IN ('ACTIVE', 'DISABLED'))
);

CREATE TABLE dbo.iam_permission (
    id bigint IDENTITY(1,1) NOT NULL CONSTRAINT pk_iam_permission PRIMARY KEY,
    permission_code varchar(128) NOT NULL,
    permission_name nvarchar(100) NOT NULL,
    resource_type varchar(32) NOT NULL,
    api_pattern varchar(255) NULL,
    http_method varchar(16) NULL,
    created_at datetime2(3) NOT NULL CONSTRAINT df_iam_permission_created_at DEFAULT (sysdatetime()),
    CONSTRAINT uq_iam_permission_code UNIQUE (permission_code),
    CONSTRAINT ck_iam_permission_resource CHECK (resource_type IN ('MENU', 'BUTTON', 'API', 'FIELD'))
);

CREATE TABLE dbo.iam_user_role (
    user_id bigint NOT NULL,
    role_id bigint NOT NULL,
    assigned_at datetime2(3) NOT NULL CONSTRAINT df_iam_user_role_assigned DEFAULT (sysdatetime()),
    assigned_by bigint NULL,
    CONSTRAINT pk_iam_user_role PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_iam_user_role_user FOREIGN KEY (user_id) REFERENCES dbo.iam_user(id),
    CONSTRAINT fk_iam_user_role_role FOREIGN KEY (role_id) REFERENCES dbo.iam_role(id),
    CONSTRAINT fk_iam_user_role_assigner FOREIGN KEY (assigned_by) REFERENCES dbo.iam_user(id)
);

CREATE TABLE dbo.iam_role_permission (
    role_id bigint NOT NULL,
    permission_id bigint NOT NULL,
    effect varchar(8) NOT NULL CONSTRAINT df_iam_role_permission_effect DEFAULT ('ALLOW'),
    field_mask_rule varchar(64) NULL,
    CONSTRAINT pk_iam_role_permission PRIMARY KEY (role_id, permission_id),
    CONSTRAINT fk_iam_role_permission_role FOREIGN KEY (role_id) REFERENCES dbo.iam_role(id),
    CONSTRAINT fk_iam_role_permission_permission FOREIGN KEY (permission_id) REFERENCES dbo.iam_permission(id),
    CONSTRAINT ck_iam_role_permission_effect CHECK (effect IN ('ALLOW', 'DENY'))
);

CREATE TABLE dbo.iam_role_store_scope (
    role_id bigint NOT NULL,
    store_id bigint NOT NULL,
    CONSTRAINT pk_iam_role_store_scope PRIMARY KEY (role_id, store_id),
    CONSTRAINT fk_iam_role_store_scope_role FOREIGN KEY (role_id) REFERENCES dbo.iam_role(id),
    CONSTRAINT fk_iam_role_store_scope_store FOREIGN KEY (store_id) REFERENCES dbo.org_store(id)
);

CREATE TABLE dbo.iam_menu (
    id bigint IDENTITY(1,1) NOT NULL CONSTRAINT pk_iam_menu PRIMARY KEY,
    parent_id bigint NULL,
    menu_code varchar(64) NOT NULL,
    name nvarchar(100) NOT NULL,
    route varchar(255) NULL,
    component varchar(255) NULL,
    icon varchar(64) NULL,
    sort_no int NOT NULL CONSTRAINT df_iam_menu_sort DEFAULT (0),
    client_type varchar(16) NOT NULL CONSTRAINT df_iam_menu_client DEFAULT ('PC'),
    permission_code varchar(128) NULL,
    status varchar(32) NOT NULL CONSTRAINT df_iam_menu_status DEFAULT ('ACTIVE'),
    created_at datetime2(3) NOT NULL CONSTRAINT df_iam_menu_created_at DEFAULT (sysdatetime()),
    updated_at datetime2(3) NOT NULL CONSTRAINT df_iam_menu_updated_at DEFAULT (sysdatetime()),
    row_version rowversion NOT NULL,
    CONSTRAINT uq_iam_menu_code UNIQUE (menu_code),
    CONSTRAINT fk_iam_menu_parent FOREIGN KEY (parent_id) REFERENCES dbo.iam_menu(id),
    CONSTRAINT fk_iam_menu_permission FOREIGN KEY (permission_code) REFERENCES dbo.iam_permission(permission_code),
    CONSTRAINT ck_iam_menu_client CHECK (client_type IN ('PC', 'MOBILE', 'HOME')),
    CONSTRAINT ck_iam_menu_status CHECK (status IN ('ACTIVE', 'DISABLED'))
);

CREATE INDEX ix_iam_menu_parent_sort
    ON dbo.iam_menu (parent_id, client_type, sort_no);

CREATE TABLE dbo.SPRING_SESSION (
    PRIMARY_ID char(36) NOT NULL,
    SESSION_ID char(36) NOT NULL,
    CREATION_TIME bigint NOT NULL,
    LAST_ACCESS_TIME bigint NOT NULL,
    MAX_INACTIVE_INTERVAL int NOT NULL,
    EXPIRY_TIME bigint NOT NULL,
    PRINCIPAL_NAME nvarchar(100) NULL,
    CONSTRAINT SPRING_SESSION_PK PRIMARY KEY (PRIMARY_ID)
);

CREATE UNIQUE INDEX SPRING_SESSION_IX1 ON dbo.SPRING_SESSION (SESSION_ID);
CREATE INDEX SPRING_SESSION_IX2 ON dbo.SPRING_SESSION (EXPIRY_TIME);
CREATE INDEX SPRING_SESSION_IX3 ON dbo.SPRING_SESSION (PRINCIPAL_NAME) WHERE PRINCIPAL_NAME IS NOT NULL;

CREATE TABLE dbo.SPRING_SESSION_ATTRIBUTES (
    SESSION_PRIMARY_ID char(36) NOT NULL,
    ATTRIBUTE_NAME nvarchar(200) NOT NULL,
    ATTRIBUTE_BYTES varbinary(max) NOT NULL,
    CONSTRAINT SPRING_SESSION_ATTRIBUTES_PK PRIMARY KEY (SESSION_PRIMARY_ID, ATTRIBUTE_NAME),
    CONSTRAINT SPRING_SESSION_ATTRIBUTES_FK FOREIGN KEY (SESSION_PRIMARY_ID)
        REFERENCES dbo.SPRING_SESSION(PRIMARY_ID) ON DELETE CASCADE
);

INSERT INTO dbo.org_organization (org_code, org_name, org_type, path, sort_no)
VALUES ('HQ', N'悦指间总部', 'COMPANY', '/HQ/', 10);

INSERT INTO dbo.org_organization (parent_id, org_code, org_name, org_type, path, sort_no)
SELECT id, 'S001', N'悦指间示范店', 'STORE', '/HQ/S001/', 10
FROM dbo.org_organization WHERE org_code = 'HQ';

INSERT INTO dbo.org_store (organization_id, store_code, store_name, store_level)
SELECT id, 'S001', N'悦指间示范店', 'A'
FROM dbo.org_organization WHERE org_code = 'S001';

INSERT INTO dbo.iam_role (role_code, role_name, data_scope_type, description)
VALUES
    ('HEADQUARTERS_ADMIN', N'总部管理员', 'ALL', N'总部全部数据和配置权限'),
    ('STORE_MANAGER', N'店长', 'STORE', N'当前门店经营管理权限');

INSERT INTO dbo.iam_permission (permission_code, permission_name, resource_type, api_pattern, http_method)
VALUES
    ('workbench:view', N'查看工作台', 'MENU', '/api/v1/workbench/**', 'GET'),
    ('org:store:view', N'查看门店', 'MENU', '/api/v1/stores/**', 'GET'),
    ('iam:role:view', N'查看角色', 'MENU', '/api/v1/roles/**', 'GET'),
    ('iam:user:view', N'查看用户', 'MENU', '/api/v1/users/**', 'GET'),
    ('member:member:view', N'查看会员', 'MENU', '/api/v1/members/**', 'GET'),
    ('member:member:create', N'新建会员', 'BUTTON', '/api/v1/members', 'POST'),
    ('appointment:appointment:view', N'查看预约', 'MENU', '/api/v1/appointments/**', 'GET'),
    ('appointment:appointment:create', N'新建预约', 'BUTTON', '/api/v1/appointments', 'POST'),
    ('trade:bill:view', N'查看账单', 'MENU', '/api/v1/bills/**', 'GET'),
    ('trade:bill:create', N'新建账单', 'BUTTON', '/api/v1/bills', 'POST');

INSERT INTO dbo.iam_role_permission (role_id, permission_id, effect)
SELECT r.id, p.id, 'ALLOW'
FROM dbo.iam_role r
CROSS JOIN dbo.iam_permission p
WHERE r.role_code = 'HEADQUARTERS_ADMIN';

INSERT INTO dbo.iam_menu (menu_code, name, route, icon, sort_no, client_type, permission_code)
VALUES
    ('workbench', N'工作台', '/app/workbench', 'HomeFilled', 10, 'PC', 'workbench:view'),
    ('member', N'会员管理', '/app/members', 'User', 20, 'PC', 'member:member:view'),
    ('appointment', N'预约管理', '/app/appointments', 'Calendar', 30, 'PC', 'appointment:appointment:view'),
    ('bill', N'账单管理', '/app/bills', 'Tickets', 40, 'PC', 'trade:bill:view'),
    ('system', N'系统管理', '/app/system', 'Setting', 90, 'PC', NULL);

INSERT INTO dbo.iam_menu (parent_id, menu_code, name, route, icon, sort_no, client_type, permission_code)
SELECT id, 'stores', N'组织门店', '/app/system/stores', 'Shop', 10, 'PC', 'org:store:view'
FROM dbo.iam_menu WHERE menu_code = 'system';

INSERT INTO dbo.iam_menu (parent_id, menu_code, name, route, icon, sort_no, client_type, permission_code)
SELECT id, 'roles', N'角色管理', '/app/system/roles', 'Lock', 20, 'PC', 'iam:role:view'
FROM dbo.iam_menu WHERE menu_code = 'system';

-- 验证 SQL：预期权限 10、角色 2、菜单 7、门店 1。
-- SELECT (SELECT COUNT(*) FROM dbo.iam_permission) permission_count,
--        (SELECT COUNT(*) FROM dbo.iam_role) role_count,
--        (SELECT COUNT(*) FROM dbo.iam_menu) menu_count,
--        (SELECT COUNT(*) FROM dbo.org_store) store_count;
