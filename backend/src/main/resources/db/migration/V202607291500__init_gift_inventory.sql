-- 需求：系统管理-23/24/25、API-CAT-023、API-INV-001~006、UI-INV-001~003
-- 目的：建立礼品主档、门店库存、不可变流水、调拨和盘点闭环。

INSERT INTO dbo.cat_category (category_type, category_code, name, path, sort_no)
VALUES ('GIFT', 'POINT_GIFT', N'积分礼品', '/GIFT/POINT_GIFT/', 10);

CREATE TABLE dbo.cat_gift (
    id bigint IDENTITY(1,1) NOT NULL CONSTRAINT pk_cat_gift PRIMARY KEY,
    gift_code varchar(64) NOT NULL,
    gift_name nvarchar(200) NOT NULL,
    category_id bigint NOT NULL,
    unit_id bigint NOT NULL,
    point_price int NOT NULL,
    cost_price decimal(19,4) NOT NULL CONSTRAINT df_cat_gift_cost DEFAULT (0),
    low_stock_threshold decimal(19,4) NOT NULL CONSTRAINT df_cat_gift_low_stock DEFAULT (0),
    description nvarchar(1000) NULL,
    status varchar(32) NOT NULL CONSTRAINT df_cat_gift_status DEFAULT ('ACTIVE'),
    created_at datetime2(3) NOT NULL CONSTRAINT df_cat_gift_created DEFAULT (sysdatetime()),
    created_by bigint NOT NULL,
    updated_at datetime2(3) NOT NULL CONSTRAINT df_cat_gift_updated DEFAULT (sysdatetime()),
    updated_by bigint NOT NULL,
    row_version rowversion NOT NULL,
    CONSTRAINT uq_cat_gift_code UNIQUE (gift_code),
    CONSTRAINT fk_cat_gift_category FOREIGN KEY (category_id) REFERENCES dbo.cat_category(id),
    CONSTRAINT fk_cat_gift_unit FOREIGN KEY (unit_id) REFERENCES dbo.cat_unit(id),
    CONSTRAINT fk_cat_gift_creator FOREIGN KEY (created_by) REFERENCES dbo.iam_user(id),
    CONSTRAINT fk_cat_gift_updater FOREIGN KEY (updated_by) REFERENCES dbo.iam_user(id),
    CONSTRAINT ck_cat_gift_name CHECK (LEN(LTRIM(RTRIM(gift_name))) > 0),
    CONSTRAINT ck_cat_gift_points CHECK (point_price BETWEEN 1 AND 100000000),
    CONSTRAINT ck_cat_gift_amount CHECK (cost_price >= 0 AND low_stock_threshold >= 0),
    CONSTRAINT ck_cat_gift_status CHECK (status IN ('ACTIVE', 'DISABLED'))
);

CREATE INDEX ix_cat_gift_status_name ON dbo.cat_gift (status, gift_name, id DESC);

CREATE TABLE dbo.inv_stock (
    id bigint IDENTITY(1,1) NOT NULL CONSTRAINT pk_inv_stock PRIMARY KEY,
    store_id bigint NOT NULL,
    gift_id bigint NOT NULL,
    on_hand_quantity decimal(19,4) NOT NULL CONSTRAINT df_inv_stock_on_hand DEFAULT (0),
    created_at datetime2(3) NOT NULL CONSTRAINT df_inv_stock_created DEFAULT (sysdatetime()),
    created_by bigint NOT NULL,
    updated_at datetime2(3) NOT NULL CONSTRAINT df_inv_stock_updated DEFAULT (sysdatetime()),
    updated_by bigint NOT NULL,
    row_version rowversion NOT NULL,
    CONSTRAINT uq_inv_stock_store_gift UNIQUE (store_id, gift_id),
    CONSTRAINT fk_inv_stock_store FOREIGN KEY (store_id) REFERENCES dbo.org_store(id),
    CONSTRAINT fk_inv_stock_gift FOREIGN KEY (gift_id) REFERENCES dbo.cat_gift(id),
    CONSTRAINT fk_inv_stock_creator FOREIGN KEY (created_by) REFERENCES dbo.iam_user(id),
    CONSTRAINT fk_inv_stock_updater FOREIGN KEY (updated_by) REFERENCES dbo.iam_user(id),
    CONSTRAINT ck_inv_stock_nonnegative CHECK (on_hand_quantity >= 0)
);

CREATE INDEX ix_inv_stock_gift_store ON dbo.inv_stock (gift_id, store_id) INCLUDE (on_hand_quantity);

CREATE TABLE dbo.inv_stock_ledger (
    id bigint IDENTITY(1,1) NOT NULL CONSTRAINT pk_inv_stock_ledger PRIMARY KEY,
    ledger_no varchar(40) NOT NULL,
    store_id bigint NOT NULL,
    gift_id bigint NOT NULL,
    transaction_type varchar(32) NOT NULL,
    before_quantity decimal(19,4) NOT NULL,
    change_quantity decimal(19,4) NOT NULL,
    after_quantity decimal(19,4) NOT NULL,
    source_type varchar(32) NOT NULL,
    source_id bigint NOT NULL,
    source_line_id bigint NULL,
    occurred_at datetime2(3) NOT NULL,
    reversed_ledger_id bigint NULL,
    note nvarchar(500) NULL,
    created_by bigint NOT NULL,
    CONSTRAINT uq_inv_stock_ledger_no UNIQUE (ledger_no),
    CONSTRAINT fk_inv_stock_ledger_store FOREIGN KEY (store_id) REFERENCES dbo.org_store(id),
    CONSTRAINT fk_inv_stock_ledger_gift FOREIGN KEY (gift_id) REFERENCES dbo.cat_gift(id),
    CONSTRAINT fk_inv_stock_ledger_reversed FOREIGN KEY (reversed_ledger_id) REFERENCES dbo.inv_stock_ledger(id),
    CONSTRAINT fk_inv_stock_ledger_creator FOREIGN KEY (created_by) REFERENCES dbo.iam_user(id),
    CONSTRAINT ck_inv_stock_ledger_type CHECK (transaction_type IN (
        'TRANSFER_OUT', 'TRANSFER_IN', 'TRANSFER_REVERSAL_OUT', 'TRANSFER_REVERSAL_IN',
        'COUNT_GAIN', 'COUNT_LOSS', 'POINT_REDEMPTION_OUT', 'POINT_REDEMPTION_REVERSAL_IN'
    )),
    CONSTRAINT ck_inv_stock_ledger_source CHECK (source_type IN ('TRANSFER', 'TRANSFER_REVERSAL', 'COUNT', 'POINT_REDEMPTION')),
    CONSTRAINT ck_inv_stock_ledger_change CHECK (change_quantity <> 0),
    CONSTRAINT ck_inv_stock_ledger_equation CHECK (after_quantity = before_quantity + change_quantity),
    CONSTRAINT ck_inv_stock_ledger_nonnegative CHECK (before_quantity >= 0 AND after_quantity >= 0)
);

CREATE UNIQUE INDEX uq_inv_stock_ledger_source
    ON dbo.inv_stock_ledger (source_type, source_id, source_line_id, store_id, transaction_type)
    WHERE source_line_id IS NOT NULL;
CREATE UNIQUE INDEX uq_inv_stock_ledger_reversal
    ON dbo.inv_stock_ledger (reversed_ledger_id) WHERE reversed_ledger_id IS NOT NULL;
CREATE INDEX ix_inv_stock_ledger_store_gift_time
    ON dbo.inv_stock_ledger (store_id, gift_id, occurred_at DESC, id DESC)
    INCLUDE (transaction_type, change_quantity, after_quantity, source_type, source_id);

CREATE TABLE dbo.inv_transfer (
    id bigint IDENTITY(1,1) NOT NULL CONSTRAINT pk_inv_transfer PRIMARY KEY,
    transfer_no varchar(40) NOT NULL,
    source_store_id bigint NOT NULL,
    target_store_id bigint NOT NULL,
    transfer_date date NOT NULL,
    remarks nvarchar(500) NULL,
    status varchar(32) NOT NULL CONSTRAINT df_inv_transfer_status DEFAULT ('DRAFT'),
    confirmed_at datetime2(3) NULL,
    voided_at datetime2(3) NULL,
    reversed_at datetime2(3) NULL,
    action_reason nvarchar(500) NULL,
    idempotency_key varchar(128) NOT NULL,
    created_at datetime2(3) NOT NULL CONSTRAINT df_inv_transfer_created DEFAULT (sysdatetime()),
    created_by bigint NOT NULL,
    updated_at datetime2(3) NOT NULL CONSTRAINT df_inv_transfer_updated DEFAULT (sysdatetime()),
    updated_by bigint NOT NULL,
    row_version rowversion NOT NULL,
    CONSTRAINT uq_inv_transfer_no UNIQUE (transfer_no),
    CONSTRAINT uq_inv_transfer_idempotency UNIQUE (idempotency_key),
    CONSTRAINT fk_inv_transfer_source_store FOREIGN KEY (source_store_id) REFERENCES dbo.org_store(id),
    CONSTRAINT fk_inv_transfer_target_store FOREIGN KEY (target_store_id) REFERENCES dbo.org_store(id),
    CONSTRAINT fk_inv_transfer_creator FOREIGN KEY (created_by) REFERENCES dbo.iam_user(id),
    CONSTRAINT fk_inv_transfer_updater FOREIGN KEY (updated_by) REFERENCES dbo.iam_user(id),
    CONSTRAINT ck_inv_transfer_stores CHECK (source_store_id <> target_store_id),
    CONSTRAINT ck_inv_transfer_status CHECK (status IN ('DRAFT', 'CONFIRMED', 'VOIDED', 'REVERSED')),
    CONSTRAINT ck_inv_transfer_times CHECK (
        (status = 'DRAFT' AND confirmed_at IS NULL AND voided_at IS NULL AND reversed_at IS NULL)
        OR (status = 'CONFIRMED' AND confirmed_at IS NOT NULL AND voided_at IS NULL AND reversed_at IS NULL)
        OR (status = 'VOIDED' AND confirmed_at IS NULL AND voided_at IS NOT NULL AND reversed_at IS NULL)
        OR (status = 'REVERSED' AND confirmed_at IS NOT NULL AND voided_at IS NULL AND reversed_at IS NOT NULL)
    )
);

CREATE INDEX ix_inv_transfer_source_date ON dbo.inv_transfer (source_store_id, transfer_date DESC, id DESC);
CREATE INDEX ix_inv_transfer_target_date ON dbo.inv_transfer (target_store_id, transfer_date DESC, id DESC);

CREATE TABLE dbo.inv_transfer_line (
    id bigint IDENTITY(1,1) NOT NULL CONSTRAINT pk_inv_transfer_line PRIMARY KEY,
    transfer_id bigint NOT NULL,
    gift_id bigint NOT NULL,
    quantity decimal(19,4) NOT NULL,
    note nvarchar(200) NULL,
    source_ledger_id bigint NULL,
    target_ledger_id bigint NULL,
    CONSTRAINT uq_inv_transfer_line_gift UNIQUE (transfer_id, gift_id),
    CONSTRAINT fk_inv_transfer_line_header FOREIGN KEY (transfer_id) REFERENCES dbo.inv_transfer(id),
    CONSTRAINT fk_inv_transfer_line_gift FOREIGN KEY (gift_id) REFERENCES dbo.cat_gift(id),
    CONSTRAINT fk_inv_transfer_line_source_ledger FOREIGN KEY (source_ledger_id) REFERENCES dbo.inv_stock_ledger(id),
    CONSTRAINT fk_inv_transfer_line_target_ledger FOREIGN KEY (target_ledger_id) REFERENCES dbo.inv_stock_ledger(id),
    CONSTRAINT ck_inv_transfer_line_quantity CHECK (quantity > 0),
    CONSTRAINT ck_inv_transfer_line_ledgers CHECK (
        (source_ledger_id IS NULL AND target_ledger_id IS NULL)
        OR (source_ledger_id IS NOT NULL AND target_ledger_id IS NOT NULL)
    )
);

CREATE INDEX ix_inv_transfer_line_gift ON dbo.inv_transfer_line (gift_id, transfer_id);

CREATE TABLE dbo.inv_count (
    id bigint IDENTITY(1,1) NOT NULL CONSTRAINT pk_inv_count PRIMARY KEY,
    count_no varchar(40) NOT NULL,
    name nvarchar(100) NOT NULL,
    store_id bigint NOT NULL,
    count_date date NOT NULL,
    remarks nvarchar(500) NULL,
    status varchar(32) NOT NULL CONSTRAINT df_inv_count_status DEFAULT ('DRAFT'),
    confirmed_at datetime2(3) NULL,
    voided_at datetime2(3) NULL,
    action_reason nvarchar(500) NULL,
    idempotency_key varchar(128) NOT NULL,
    created_at datetime2(3) NOT NULL CONSTRAINT df_inv_count_created DEFAULT (sysdatetime()),
    created_by bigint NOT NULL,
    updated_at datetime2(3) NOT NULL CONSTRAINT df_inv_count_updated DEFAULT (sysdatetime()),
    updated_by bigint NOT NULL,
    row_version rowversion NOT NULL,
    CONSTRAINT uq_inv_count_no UNIQUE (count_no),
    CONSTRAINT uq_inv_count_idempotency UNIQUE (idempotency_key),
    CONSTRAINT fk_inv_count_store FOREIGN KEY (store_id) REFERENCES dbo.org_store(id),
    CONSTRAINT fk_inv_count_creator FOREIGN KEY (created_by) REFERENCES dbo.iam_user(id),
    CONSTRAINT fk_inv_count_updater FOREIGN KEY (updated_by) REFERENCES dbo.iam_user(id),
    CONSTRAINT ck_inv_count_name CHECK (LEN(LTRIM(RTRIM(name))) > 0),
    CONSTRAINT ck_inv_count_status CHECK (status IN ('DRAFT', 'READY_CONFIRM', 'CONFIRMED', 'VOIDED')),
    CONSTRAINT ck_inv_count_times CHECK (
        (status IN ('DRAFT', 'READY_CONFIRM') AND confirmed_at IS NULL AND voided_at IS NULL)
        OR (status = 'CONFIRMED' AND confirmed_at IS NOT NULL AND voided_at IS NULL)
        OR (status = 'VOIDED' AND confirmed_at IS NULL AND voided_at IS NOT NULL)
    )
);

CREATE INDEX ix_inv_count_store_date ON dbo.inv_count (store_id, count_date DESC, id DESC);

CREATE TABLE dbo.inv_count_line (
    id bigint IDENTITY(1,1) NOT NULL CONSTRAINT pk_inv_count_line PRIMARY KEY,
    count_id bigint NOT NULL,
    gift_id bigint NOT NULL,
    book_quantity decimal(19,4) NOT NULL,
    actual_quantity decimal(19,4) NULL,
    stock_ledger_id bigint NULL,
    CONSTRAINT uq_inv_count_line_gift UNIQUE (count_id, gift_id),
    CONSTRAINT fk_inv_count_line_header FOREIGN KEY (count_id) REFERENCES dbo.inv_count(id),
    CONSTRAINT fk_inv_count_line_gift FOREIGN KEY (gift_id) REFERENCES dbo.cat_gift(id),
    CONSTRAINT fk_inv_count_line_ledger FOREIGN KEY (stock_ledger_id) REFERENCES dbo.inv_stock_ledger(id),
    CONSTRAINT ck_inv_count_line_quantity CHECK (
        book_quantity >= 0 AND (actual_quantity IS NULL OR actual_quantity >= 0)
    )
);

CREATE INDEX ix_inv_count_line_gift ON dbo.inv_count_line (gift_id, count_id);

INSERT INTO dbo.iam_permission (permission_code, permission_name, resource_type, api_pattern, http_method)
VALUES
    ('inventory:gift:view', N'查看礼品资料', 'MENU', '/api/v1/gifts/**', 'GET'),
    ('inventory:gift:manage', N'维护礼品资料', 'BUTTON', '/api/v1/gifts/**', 'POST,PUT'),
    ('inventory:stock:view', N'查看礼品库存', 'MENU', '/api/v1/inventories/**', 'GET'),
    ('inventory:transfer:view', N'查看礼品调拨', 'MENU', '/api/v1/inventory-transfers/**', 'GET'),
    ('inventory:transfer:manage', N'处理礼品调拨', 'BUTTON', '/api/v1/inventory-transfers/**', 'POST'),
    ('inventory:count:view', N'查看礼品盘点', 'MENU', '/api/v1/inventory-counts/**', 'GET'),
    ('inventory:count:manage', N'处理礼品盘点', 'BUTTON', '/api/v1/inventory-counts/**', 'POST,PUT');

INSERT INTO dbo.iam_role_permission (role_id, permission_id, effect)
SELECT role.id, permission.id, 'ALLOW'
FROM dbo.iam_role role CROSS JOIN dbo.iam_permission permission
WHERE role.role_code = 'HEADQUARTERS_ADMIN'
  AND permission.permission_code IN (
      'inventory:gift:view', 'inventory:gift:manage', 'inventory:stock:view',
      'inventory:transfer:view', 'inventory:transfer:manage',
      'inventory:count:view', 'inventory:count:manage'
  );

INSERT INTO dbo.iam_role_permission (role_id, permission_id, effect)
SELECT role.id, permission.id, 'ALLOW'
FROM dbo.iam_role role CROSS JOIN dbo.iam_permission permission
WHERE role.role_code = 'STORE_MANAGER'
  AND permission.permission_code IN (
      'inventory:gift:view', 'inventory:stock:view', 'inventory:transfer:view',
      'inventory:count:view', 'inventory:count:manage'
  );

INSERT INTO dbo.iam_menu (parent_id, menu_code, name, route, icon, sort_no, client_type, permission_code)
VALUES (NULL, 'inventory', N'礼品库存', '/app/inventory/gifts', 'Box', 62, 'PC', NULL);

INSERT INTO dbo.iam_menu (parent_id, menu_code, name, route, icon, sort_no, client_type, permission_code)
SELECT id, 'gift-inventory', N'礼品库存', '/app/inventory/gifts', 'Goods', 10, 'PC', 'inventory:stock:view'
FROM dbo.iam_menu WHERE menu_code = 'inventory';

INSERT INTO dbo.iam_menu (parent_id, menu_code, name, route, icon, sort_no, client_type, permission_code)
SELECT id, 'inventory-transfers', N'礼品调拨', '/app/inventory/transfers', 'Switch', 20, 'PC', 'inventory:transfer:view'
FROM dbo.iam_menu WHERE menu_code = 'inventory';

INSERT INTO dbo.iam_menu (parent_id, menu_code, name, route, icon, sort_no, client_type, permission_code)
SELECT id, 'inventory-counts', N'礼品盘点', '/app/inventory/counts', 'Checked', 30, 'PC', 'inventory:count:view'
FROM dbo.iam_menu WHERE menu_code = 'inventory';

-- 验证：礼品、库存、流水、调拨和盘点共7张表；7项权限和4个菜单。
