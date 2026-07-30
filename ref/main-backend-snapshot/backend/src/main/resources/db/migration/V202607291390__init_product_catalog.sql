-- 需求：系统管理-11/12、API-CAT-001/003/004/005、UI-CAT-001
-- 目的：建立产品主档、门店售价和产品管理入口。

CREATE TABLE dbo.cat_product (
    id bigint IDENTITY(1,1) NOT NULL CONSTRAINT pk_cat_product PRIMARY KEY,
    product_code varchar(64) NOT NULL,
    product_name nvarchar(200) NOT NULL,
    category_id bigint NOT NULL,
    unit_id bigint NOT NULL,
    barcode varchar(64) NULL,
    cost_price decimal(19,4) NOT NULL CONSTRAINT df_cat_product_cost DEFAULT (0),
    sale_price decimal(19,4) NOT NULL,
    track_stock bit NOT NULL CONSTRAINT df_cat_product_track_stock DEFAULT (1),
    description nvarchar(1000) NULL,
    status varchar(32) NOT NULL CONSTRAINT df_cat_product_status DEFAULT ('ACTIVE'),
    created_at datetime2(3) NOT NULL CONSTRAINT df_cat_product_created_at DEFAULT (sysdatetime()),
    created_by bigint NULL,
    updated_at datetime2(3) NOT NULL CONSTRAINT df_cat_product_updated_at DEFAULT (sysdatetime()),
    updated_by bigint NULL,
    row_version rowversion NOT NULL,
    CONSTRAINT uq_cat_product_code UNIQUE (product_code),
    CONSTRAINT fk_cat_product_category FOREIGN KEY (category_id) REFERENCES dbo.cat_category(id),
    CONSTRAINT fk_cat_product_unit FOREIGN KEY (unit_id) REFERENCES dbo.cat_unit(id),
    CONSTRAINT ck_cat_product_amount CHECK (cost_price >= 0 AND sale_price >= 0),
    CONSTRAINT ck_cat_product_status CHECK (status IN ('ACTIVE', 'DISABLED'))
);

CREATE UNIQUE INDEX uq_cat_product_barcode
    ON dbo.cat_product (barcode)
    WHERE barcode IS NOT NULL;

CREATE INDEX ix_cat_product_category_status
    ON dbo.cat_product (category_id, status, id DESC);

INSERT INTO dbo.cat_category (category_type, category_code, name, path, sort_no)
VALUES ('PRODUCT', 'RETAIL_PRODUCT', N'零售产品', '/PRODUCT/RETAIL_PRODUCT/', 10);

INSERT INTO dbo.iam_permission (permission_code, permission_name, resource_type, api_pattern, http_method)
VALUES
    ('catalog:product:view', N'查看产品资料', 'MENU', '/api/v1/products/**', 'GET'),
    ('catalog:product:manage', N'维护产品资料', 'BUTTON', '/api/v1/products/**', 'POST');

INSERT INTO dbo.iam_role_permission (role_id, permission_id, effect)
SELECT role.id, permission.id, 'ALLOW'
FROM dbo.iam_role role CROSS JOIN dbo.iam_permission permission
WHERE role.role_code = 'HEADQUARTERS_ADMIN'
  AND permission.permission_code IN ('catalog:product:view', 'catalog:product:manage');

INSERT INTO dbo.iam_menu (parent_id, menu_code, name, route, icon, sort_no, client_type, permission_code)
SELECT id, 'products', N'产品管理', '/app/catalog/products', 'Goods', 5,
       'PC', 'catalog:product:view'
FROM dbo.iam_menu WHERE menu_code = 'catalog';
