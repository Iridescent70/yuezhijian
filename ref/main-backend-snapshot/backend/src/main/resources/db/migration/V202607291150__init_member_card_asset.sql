-- 需求：API-CAT-012/013/016、API-AST-008~010、系统管理-13、次卡管理-05
-- 目的：建立次卡类型、适用门店、项目次数规则、售卡订单、会员次卡和不可变次数流水。

INSERT INTO dbo.cat_category (category_type, category_code, name, path, sort_no)
VALUES ('CARD', 'SERVICE_CARD', N'服务次卡', '/CARD/SERVICE_CARD/', 10);

CREATE TABLE dbo.cat_card_type (
    id bigint IDENTITY(1,1) NOT NULL CONSTRAINT pk_cat_card_type PRIMARY KEY,
    card_type_code varchar(64) NOT NULL,
    card_type_name nvarchar(200) NOT NULL,
    category_id bigint NOT NULL,
    sale_price decimal(19,4) NOT NULL,
    list_price decimal(19,4) NOT NULL,
    total_times decimal(19,4) NOT NULL,
    valid_days int NOT NULL,
    purchase_threshold decimal(19,4) NOT NULL CONSTRAINT df_cat_card_threshold DEFAULT (0),
    instructions nvarchar(max) NULL,
    auto_remind_days int NOT NULL CONSTRAINT df_cat_card_remind DEFAULT (30),
    status varchar(32) NOT NULL CONSTRAINT df_cat_card_status DEFAULT ('ACTIVE'),
    created_at datetime2(3) NOT NULL CONSTRAINT df_cat_card_created DEFAULT (sysdatetime()),
    created_by bigint NOT NULL,
    updated_at datetime2(3) NOT NULL CONSTRAINT df_cat_card_updated DEFAULT (sysdatetime()),
    updated_by bigint NOT NULL,
    row_version rowversion NOT NULL,
    CONSTRAINT uq_cat_card_type_code UNIQUE (card_type_code),
    CONSTRAINT fk_cat_card_type_category FOREIGN KEY (category_id) REFERENCES dbo.cat_category(id),
    CONSTRAINT ck_cat_card_type_amount CHECK (
        sale_price >= 0 AND list_price >= sale_price AND total_times > 0 AND purchase_threshold >= 0
    ),
    CONSTRAINT ck_cat_card_type_period CHECK (valid_days BETWEEN 1 AND 3650 AND auto_remind_days BETWEEN 0 AND 365),
    CONSTRAINT ck_cat_card_type_status CHECK (status IN ('ACTIVE', 'DISABLED'))
);

CREATE TABLE dbo.cat_card_type_store (
    id bigint IDENTITY(1,1) NOT NULL CONSTRAINT pk_cat_card_type_store PRIMARY KEY,
    card_type_id bigint NOT NULL,
    store_id bigint NOT NULL,
    sale_status varchar(32) NOT NULL CONSTRAINT df_cat_card_store_status DEFAULT ('ON_SALE'),
    sort_no int NOT NULL CONSTRAINT df_cat_card_store_sort DEFAULT (0),
    created_at datetime2(3) NOT NULL CONSTRAINT df_cat_card_store_created DEFAULT (sysdatetime()),
    CONSTRAINT uq_cat_card_type_store UNIQUE (card_type_id, store_id),
    CONSTRAINT fk_cat_card_store_type FOREIGN KEY (card_type_id) REFERENCES dbo.cat_card_type(id),
    CONSTRAINT fk_cat_card_store_store FOREIGN KEY (store_id) REFERENCES dbo.org_store(id),
    CONSTRAINT ck_cat_card_store_status CHECK (sale_status IN ('ON_SALE', 'OFF_SALE'))
);

CREATE INDEX ix_cat_card_store_sale
    ON dbo.cat_card_type_store (store_id, sale_status, sort_no, card_type_id);

CREATE TABLE dbo.cat_card_service_rule (
    id bigint IDENTITY(1,1) NOT NULL CONSTRAINT pk_cat_card_service_rule PRIMARY KEY,
    card_type_id bigint NOT NULL,
    service_id bigint NOT NULL,
    included_times decimal(19,4) NOT NULL,
    deduct_times decimal(19,4) NOT NULL CONSTRAINT df_cat_card_rule_deduct DEFAULT (1),
    priority int NOT NULL CONSTRAINT df_cat_card_rule_priority DEFAULT (0),
    effective_from datetime2(3) NULL,
    effective_to datetime2(3) NULL,
    created_at datetime2(3) NOT NULL CONSTRAINT df_cat_card_rule_created DEFAULT (sysdatetime()),
    CONSTRAINT uq_cat_card_service_rule UNIQUE (card_type_id, service_id),
    CONSTRAINT fk_cat_card_rule_type FOREIGN KEY (card_type_id) REFERENCES dbo.cat_card_type(id),
    CONSTRAINT fk_cat_card_rule_service FOREIGN KEY (service_id) REFERENCES dbo.cat_service(id),
    CONSTRAINT ck_cat_card_rule_times CHECK (included_times > 0 AND deduct_times > 0),
    CONSTRAINT ck_cat_card_rule_period CHECK (effective_to IS NULL OR effective_from IS NULL OR effective_to >= effective_from)
);

CREATE TABLE dbo.ast_card_sale_order (
    id bigint IDENTITY(1,1) NOT NULL CONSTRAINT pk_ast_card_sale_order PRIMARY KEY,
    order_no varchar(32) NOT NULL,
    member_id bigint NOT NULL,
    card_type_id bigint NOT NULL,
    store_id bigint NOT NULL,
    quantity int NOT NULL,
    unit_price decimal(19,4) NOT NULL,
    total_amount decimal(19,4) NOT NULL,
    payment_method_id bigint NOT NULL,
    external_reference varchar(128) NULL,
    sales_employee_id bigint NULL,
    status varchar(32) NOT NULL CONSTRAINT df_ast_card_sale_status DEFAULT ('CONFIRMED'),
    idempotency_key varchar(128) NOT NULL,
    sold_at datetime2(3) NOT NULL CONSTRAINT df_ast_card_sale_time DEFAULT (sysdatetime()),
    created_by bigint NOT NULL,
    CONSTRAINT uq_ast_card_sale_no UNIQUE (order_no),
    CONSTRAINT uq_ast_card_sale_idempotency UNIQUE (idempotency_key),
    CONSTRAINT fk_ast_card_sale_member FOREIGN KEY (member_id) REFERENCES dbo.mem_member(id),
    CONSTRAINT fk_ast_card_sale_type FOREIGN KEY (card_type_id) REFERENCES dbo.cat_card_type(id),
    CONSTRAINT fk_ast_card_sale_store FOREIGN KEY (store_id) REFERENCES dbo.org_store(id),
    CONSTRAINT fk_ast_card_sale_method FOREIGN KEY (payment_method_id) REFERENCES dbo.cat_payment_method(id),
    CONSTRAINT fk_ast_card_sale_employee FOREIGN KEY (sales_employee_id) REFERENCES dbo.org_employee(id),
    CONSTRAINT fk_ast_card_sale_creator FOREIGN KEY (created_by) REFERENCES dbo.iam_user(id),
    CONSTRAINT ck_ast_card_sale_quantity CHECK (quantity BETWEEN 1 AND 20),
    CONSTRAINT ck_ast_card_sale_amount CHECK (
        unit_price >= 0 AND total_amount = unit_price * quantity
    ),
    CONSTRAINT ck_ast_card_sale_status CHECK (status IN ('CONFIRMED', 'REVERSED'))
);

CREATE TABLE dbo.ast_member_card (
    id bigint IDENTITY(1,1) NOT NULL CONSTRAINT pk_ast_member_card PRIMARY KEY,
    card_no varchar(32) NOT NULL,
    member_id bigint NOT NULL,
    card_type_id bigint NOT NULL,
    card_type_code_snapshot varchar(64) NOT NULL,
    card_type_name_snapshot nvarchar(200) NOT NULL,
    source_order_id bigint NOT NULL,
    purchase_store_id bigint NOT NULL,
    sale_employee_id bigint NULL,
    purchase_price decimal(19,4) NOT NULL,
    started_at datetime2(3) NOT NULL,
    expires_at datetime2(3) NOT NULL,
    original_card_id bigint NULL,
    transfer_from_card_id bigint NULL,
    status varchar(32) NOT NULL CONSTRAINT df_ast_member_card_status DEFAULT ('ACTIVE'),
    created_at datetime2(3) NOT NULL CONSTRAINT df_ast_member_card_created DEFAULT (sysdatetime()),
    created_by bigint NOT NULL,
    updated_at datetime2(3) NOT NULL CONSTRAINT df_ast_member_card_updated DEFAULT (sysdatetime()),
    updated_by bigint NOT NULL,
    row_version rowversion NOT NULL,
    CONSTRAINT uq_ast_member_card_no UNIQUE (card_no),
    CONSTRAINT fk_ast_member_card_member FOREIGN KEY (member_id) REFERENCES dbo.mem_member(id),
    CONSTRAINT fk_ast_member_card_type FOREIGN KEY (card_type_id) REFERENCES dbo.cat_card_type(id),
    CONSTRAINT fk_ast_member_card_order FOREIGN KEY (source_order_id) REFERENCES dbo.ast_card_sale_order(id),
    CONSTRAINT fk_ast_member_card_store FOREIGN KEY (purchase_store_id) REFERENCES dbo.org_store(id),
    CONSTRAINT fk_ast_member_card_employee FOREIGN KEY (sale_employee_id) REFERENCES dbo.org_employee(id),
    CONSTRAINT fk_ast_member_card_original FOREIGN KEY (original_card_id) REFERENCES dbo.ast_member_card(id),
    CONSTRAINT fk_ast_member_card_transfer FOREIGN KEY (transfer_from_card_id) REFERENCES dbo.ast_member_card(id),
    CONSTRAINT fk_ast_member_card_creator FOREIGN KEY (created_by) REFERENCES dbo.iam_user(id),
    CONSTRAINT fk_ast_member_card_updater FOREIGN KEY (updated_by) REFERENCES dbo.iam_user(id),
    CONSTRAINT ck_ast_member_card_price CHECK (purchase_price >= 0),
    CONSTRAINT ck_ast_member_card_period CHECK (expires_at >= started_at),
    CONSTRAINT ck_ast_member_card_status CHECK (status IN (
        'ACTIVE', 'EXHAUSTED', 'EXPIRED', 'FROZEN', 'TRANSFERRED', 'EXCHANGED', 'REFUNDED'
    ))
);

CREATE INDEX ix_ast_member_card_member_status
    ON dbo.ast_member_card (member_id, status, expires_at, id DESC);

CREATE TABLE dbo.ast_member_card_balance (
    id bigint IDENTITY(1,1) NOT NULL CONSTRAINT pk_ast_member_card_balance PRIMARY KEY,
    member_card_id bigint NOT NULL,
    service_id bigint NOT NULL,
    total_times decimal(19,4) NOT NULL,
    remaining_times decimal(19,4) NOT NULL,
    frozen_times decimal(19,4) NOT NULL CONSTRAINT df_ast_card_balance_frozen DEFAULT (0),
    deduct_times decimal(19,4) NOT NULL CONSTRAINT df_ast_card_balance_deduct DEFAULT (1),
    updated_at datetime2(3) NOT NULL CONSTRAINT df_ast_card_balance_updated DEFAULT (sysdatetime()),
    row_version rowversion NOT NULL,
    CONSTRAINT uq_ast_member_card_balance UNIQUE (member_card_id, service_id),
    CONSTRAINT fk_ast_card_balance_card FOREIGN KEY (member_card_id) REFERENCES dbo.ast_member_card(id),
    CONSTRAINT fk_ast_card_balance_service FOREIGN KEY (service_id) REFERENCES dbo.cat_service(id),
    CONSTRAINT ck_ast_card_balance_times CHECK (
        total_times > 0 AND remaining_times >= 0 AND frozen_times >= 0 AND deduct_times > 0
        AND remaining_times + frozen_times <= total_times
    )
);

CREATE TABLE dbo.ast_member_card_ledger (
    id bigint IDENTITY(1,1) NOT NULL CONSTRAINT pk_ast_member_card_ledger PRIMARY KEY,
    ledger_no varchar(32) NOT NULL,
    member_card_id bigint NOT NULL,
    service_id bigint NOT NULL,
    transaction_type varchar(32) NOT NULL,
    before_times decimal(19,4) NOT NULL,
    change_times decimal(19,4) NOT NULL,
    after_times decimal(19,4) NOT NULL,
    value_amount decimal(19,4) NOT NULL CONSTRAINT df_ast_card_ledger_value DEFAULT (0),
    source_type varchar(32) NOT NULL,
    source_id bigint NOT NULL,
    source_line_id bigint NULL,
    occurred_at datetime2(3) NOT NULL CONSTRAINT df_ast_card_ledger_time DEFAULT (sysdatetime()),
    correlation_id varchar(128) NOT NULL,
    reversed_ledger_id bigint NULL,
    note nvarchar(500) NULL,
    created_by bigint NOT NULL,
    CONSTRAINT uq_ast_card_ledger_no UNIQUE (ledger_no),
    CONSTRAINT uq_ast_card_ledger_correlation UNIQUE (correlation_id),
    CONSTRAINT fk_ast_card_ledger_card FOREIGN KEY (member_card_id) REFERENCES dbo.ast_member_card(id),
    CONSTRAINT fk_ast_card_ledger_service FOREIGN KEY (service_id) REFERENCES dbo.cat_service(id),
    CONSTRAINT fk_ast_card_ledger_reversed FOREIGN KEY (reversed_ledger_id) REFERENCES dbo.ast_member_card_ledger(id),
    CONSTRAINT fk_ast_card_ledger_creator FOREIGN KEY (created_by) REFERENCES dbo.iam_user(id),
    CONSTRAINT ck_ast_card_ledger_type CHECK (transaction_type IN (
        'PURCHASE', 'CONSUME', 'REFUND', 'EXCHANGE_OUT', 'EXCHANGE_IN',
        'TRANSFER_OUT', 'TRANSFER_IN', 'ADJUST_IN', 'ADJUST_OUT', 'REVERSAL', 'MIGRATION'
    )),
    CONSTRAINT ck_ast_card_ledger_equation CHECK (after_times = before_times + change_times),
    CONSTRAINT ck_ast_card_ledger_nonnegative CHECK (before_times >= 0 AND after_times >= 0 AND value_amount >= 0)
);

CREATE INDEX ix_ast_card_ledger_card_time
    ON dbo.ast_member_card_ledger (member_card_id, occurred_at DESC, id DESC);
CREATE INDEX ix_ast_card_ledger_source
    ON dbo.ast_member_card_ledger (source_type, source_id, id);

INSERT INTO dbo.iam_permission (permission_code, permission_name, resource_type, api_pattern, http_method)
VALUES
    ('catalog:card:view', N'查看次卡类型', 'MENU', '/api/v1/card-types/**', 'GET'),
    ('catalog:card:manage', N'维护次卡类型', 'BUTTON', '/api/v1/card-types/**', 'POST'),
    ('member:card:view', N'查看会员次卡', 'BUTTON', '/api/v1/members/*/cards', 'GET'),
    ('member:card:manage', N'办理会员次卡', 'BUTTON', '/api/v1/members/*/cards', 'POST');

INSERT INTO dbo.iam_role_permission (role_id, permission_id, effect)
SELECT role.id, permission.id, 'ALLOW'
FROM dbo.iam_role role CROSS JOIN dbo.iam_permission permission
WHERE role.role_code = 'HEADQUARTERS_ADMIN'
  AND permission.permission_code IN (
      'catalog:card:view', 'catalog:card:manage', 'member:card:view', 'member:card:manage'
  );

INSERT INTO dbo.iam_menu (parent_id, menu_code, name, route, icon, sort_no, client_type, permission_code)
SELECT id, 'card-types', N'次卡类型', '/app/catalog/card-types', 'Postcard', 20, 'PC', 'catalog:card:view'
FROM dbo.iam_menu WHERE menu_code = 'catalog';

-- 验证：新增7张表、4项权限、1个菜单；售卡必须生成项目余额和PURCHASE流水。
