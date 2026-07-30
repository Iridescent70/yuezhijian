-- 需求：API-ORG-006/008/013、API-CAT-001/003/008，预约前置主数据
-- 目的：建立服务分类、单位、服务项目、门店价格、工位以及对应权限菜单。

CREATE TABLE dbo.org_workstation (
    id bigint IDENTITY(1,1) NOT NULL CONSTRAINT pk_org_workstation PRIMARY KEY,
    store_id bigint NOT NULL,
    workstation_code varchar(64) NOT NULL,
    name nvarchar(100) NOT NULL,
    capacity int NOT NULL CONSTRAINT df_org_workstation_capacity DEFAULT (1),
    sort_no int NOT NULL CONSTRAINT df_org_workstation_sort DEFAULT (0),
    status varchar(32) NOT NULL CONSTRAINT df_org_workstation_status DEFAULT ('ACTIVE'),
    created_at datetime2(3) NOT NULL CONSTRAINT df_org_workstation_created_at DEFAULT (sysdatetime()),
    created_by bigint NULL,
    updated_at datetime2(3) NOT NULL CONSTRAINT df_org_workstation_updated_at DEFAULT (sysdatetime()),
    updated_by bigint NULL,
    row_version rowversion NOT NULL,
    CONSTRAINT uq_org_workstation_store_code UNIQUE (store_id, workstation_code),
    CONSTRAINT fk_org_workstation_store FOREIGN KEY (store_id) REFERENCES dbo.org_store(id),
    CONSTRAINT ck_org_workstation_capacity CHECK (capacity BETWEEN 1 AND 100),
    CONSTRAINT ck_org_workstation_status CHECK (status IN ('ACTIVE', 'DISABLED'))
);

CREATE INDEX ix_org_workstation_store_status
    ON dbo.org_workstation (store_id, status, sort_no);

CREATE TABLE dbo.cat_category (
    id bigint IDENTITY(1,1) NOT NULL CONSTRAINT pk_cat_category PRIMARY KEY,
    parent_id bigint NULL,
    category_type varchar(32) NOT NULL,
    category_code varchar(64) NOT NULL,
    name nvarchar(100) NOT NULL,
    path varchar(1000) NOT NULL,
    sort_no int NOT NULL CONSTRAINT df_cat_category_sort DEFAULT (0),
    status varchar(32) NOT NULL CONSTRAINT df_cat_category_status DEFAULT ('ACTIVE'),
    created_at datetime2(3) NOT NULL CONSTRAINT df_cat_category_created_at DEFAULT (sysdatetime()),
    created_by bigint NULL,
    updated_at datetime2(3) NOT NULL CONSTRAINT df_cat_category_updated_at DEFAULT (sysdatetime()),
    updated_by bigint NULL,
    row_version rowversion NOT NULL,
    CONSTRAINT uq_cat_category_type_code UNIQUE (category_type, category_code),
    CONSTRAINT fk_cat_category_parent FOREIGN KEY (parent_id) REFERENCES dbo.cat_category(id),
    CONSTRAINT ck_cat_category_type CHECK (category_type IN ('PRODUCT', 'SERVICE', 'CARD', 'GIFT')),
    CONSTRAINT ck_cat_category_status CHECK (status IN ('ACTIVE', 'DISABLED'))
);

CREATE INDEX ix_cat_category_parent_sort
    ON dbo.cat_category (category_type, parent_id, sort_no);

CREATE TABLE dbo.cat_unit (
    id bigint IDENTITY(1,1) NOT NULL CONSTRAINT pk_cat_unit PRIMARY KEY,
    unit_code varchar(32) NOT NULL,
    unit_name nvarchar(50) NOT NULL,
    decimal_places tinyint NOT NULL CONSTRAINT df_cat_unit_decimal_places DEFAULT (0),
    status varchar(32) NOT NULL CONSTRAINT df_cat_unit_status DEFAULT ('ACTIVE'),
    created_at datetime2(3) NOT NULL CONSTRAINT df_cat_unit_created_at DEFAULT (sysdatetime()),
    created_by bigint NULL,
    updated_at datetime2(3) NOT NULL CONSTRAINT df_cat_unit_updated_at DEFAULT (sysdatetime()),
    updated_by bigint NULL,
    row_version rowversion NOT NULL,
    CONSTRAINT uq_cat_unit_code UNIQUE (unit_code),
    CONSTRAINT ck_cat_unit_decimal_places CHECK (decimal_places BETWEEN 0 AND 4),
    CONSTRAINT ck_cat_unit_status CHECK (status IN ('ACTIVE', 'DISABLED'))
);

CREATE TABLE dbo.cat_service (
    id bigint IDENTITY(1,1) NOT NULL CONSTRAINT pk_cat_service PRIMARY KEY,
    service_code varchar(64) NOT NULL,
    service_name nvarchar(200) NOT NULL,
    category_id bigint NOT NULL,
    duration_minutes int NOT NULL,
    cost_amount decimal(19,4) NOT NULL CONSTRAINT df_cat_service_cost DEFAULT (0),
    list_price decimal(19,4) NOT NULL,
    description nvarchar(2000) NULL,
    instructions nvarchar(max) NULL,
    status varchar(32) NOT NULL CONSTRAINT df_cat_service_status DEFAULT ('ACTIVE'),
    created_at datetime2(3) NOT NULL CONSTRAINT df_cat_service_created_at DEFAULT (sysdatetime()),
    created_by bigint NULL,
    updated_at datetime2(3) NOT NULL CONSTRAINT df_cat_service_updated_at DEFAULT (sysdatetime()),
    updated_by bigint NULL,
    row_version rowversion NOT NULL,
    CONSTRAINT uq_cat_service_code UNIQUE (service_code),
    CONSTRAINT fk_cat_service_category FOREIGN KEY (category_id) REFERENCES dbo.cat_category(id),
    CONSTRAINT ck_cat_service_duration CHECK (duration_minutes BETWEEN 5 AND 1440),
    CONSTRAINT ck_cat_service_amount CHECK (cost_amount >= 0 AND list_price >= 0),
    CONSTRAINT ck_cat_service_status CHECK (status IN ('ACTIVE', 'DISABLED'))
);

CREATE INDEX ix_cat_service_category_status
    ON dbo.cat_service (category_id, status, id DESC);

CREATE TABLE dbo.cat_item_store (
    id bigint IDENTITY(1,1) NOT NULL CONSTRAINT pk_cat_item_store PRIMARY KEY,
    item_type varchar(16) NOT NULL,
    item_id bigint NOT NULL,
    store_id bigint NOT NULL,
    sale_price decimal(19,4) NOT NULL,
    sort_no int NOT NULL CONSTRAINT df_cat_item_store_sort DEFAULT (0),
    sale_status varchar(32) NOT NULL CONSTRAINT df_cat_item_store_sale_status DEFAULT ('ON_SALE'),
    effective_from datetime2(3) NULL,
    effective_to datetime2(3) NULL,
    created_at datetime2(3) NOT NULL CONSTRAINT df_cat_item_store_created_at DEFAULT (sysdatetime()),
    created_by bigint NULL,
    updated_at datetime2(3) NOT NULL CONSTRAINT df_cat_item_store_updated_at DEFAULT (sysdatetime()),
    updated_by bigint NULL,
    row_version rowversion NOT NULL,
    CONSTRAINT uq_cat_item_store UNIQUE (item_type, item_id, store_id),
    CONSTRAINT fk_cat_item_store_store FOREIGN KEY (store_id) REFERENCES dbo.org_store(id),
    CONSTRAINT ck_cat_item_store_type CHECK (item_type IN ('PRODUCT', 'SERVICE')),
    CONSTRAINT ck_cat_item_store_price CHECK (sale_price >= 0),
    CONSTRAINT ck_cat_item_store_sale_status CHECK (sale_status IN ('ON_SALE', 'OFF_SALE')),
    CONSTRAINT ck_cat_item_store_period CHECK (effective_to IS NULL OR effective_from IS NULL OR effective_to >= effective_from)
);

CREATE INDEX ix_cat_item_store_store_status
    ON dbo.cat_item_store (store_id, item_type, sale_status, sort_no);

INSERT INTO dbo.org_position (
    position_code, position_name, position_level, default_service_rate, default_sales_rate
)
VALUES
    ('TECHNICIAN', N'美甲技师', 10, 0, 0),
    ('STORE_MANAGER', N'店长', 20, 0, 0),
    ('RECEPTION', N'前台/收银', 5, 0, 0);

INSERT INTO dbo.cat_category (category_type, category_code, name, path, sort_no)
VALUES ('SERVICE', 'NAIL_SERVICE', N'美甲服务', '/SERVICE/NAIL_SERVICE/', 10);

INSERT INTO dbo.cat_unit (unit_code, unit_name, decimal_places)
VALUES ('TIME', N'次', 0), ('PIECE', N'件', 0), ('BOTTLE', N'瓶', 2);

INSERT INTO dbo.iam_permission (permission_code, permission_name, resource_type, api_pattern, http_method)
VALUES
    ('org:employee:view', N'查看员工', 'MENU', '/api/v1/employees/**', 'GET'),
    ('org:employee:manage', N'维护员工', 'BUTTON', '/api/v1/employees/**', 'POST'),
    ('org:workstation:view', N'查看工位', 'MENU', '/api/v1/workstations/**', 'GET'),
    ('org:workstation:manage', N'维护工位', 'BUTTON', '/api/v1/workstations/**', 'POST'),
    ('catalog:service:view', N'查看服务项目', 'MENU', '/api/v1/services/**', 'GET'),
    ('catalog:service:manage', N'维护服务项目', 'BUTTON', '/api/v1/services/**', 'POST');

INSERT INTO dbo.iam_role_permission (role_id, permission_id, effect)
SELECT r.id, p.id, 'ALLOW'
FROM dbo.iam_role r
CROSS JOIN dbo.iam_permission p
WHERE r.role_code = 'HEADQUARTERS_ADMIN'
  AND p.permission_code IN (
    'org:employee:view', 'org:employee:manage',
    'org:workstation:view', 'org:workstation:manage',
    'catalog:service:view', 'catalog:service:manage'
  );

INSERT INTO dbo.iam_menu (parent_id, menu_code, name, route, icon, sort_no, client_type, permission_code)
SELECT id, 'employees', N'员工管理', '/app/system/employees', 'UserFilled', 30, 'PC', 'org:employee:view'
FROM dbo.iam_menu WHERE menu_code = 'system';

INSERT INTO dbo.iam_menu (parent_id, menu_code, name, route, icon, sort_no, client_type, permission_code)
SELECT id, 'workstations', N'工位管理', '/app/system/workstations', 'OfficeBuilding', 40, 'PC', 'org:workstation:view'
FROM dbo.iam_menu WHERE menu_code = 'system';

INSERT INTO dbo.iam_menu (menu_code, name, route, icon, sort_no, client_type, permission_code)
VALUES ('catalog', N'基础资料', '/app/catalog', 'Collection', 60, 'PC', NULL);

INSERT INTO dbo.iam_menu (parent_id, menu_code, name, route, icon, sort_no, client_type, permission_code)
SELECT id, 'services', N'服务项目', '/app/catalog/services', 'Service', 10, 'PC', 'catalog:service:view'
FROM dbo.iam_menu WHERE menu_code = 'catalog';

-- 验证：新增5张表、6项权限、4个菜单、3个职务、1个服务分类、3个单位。
