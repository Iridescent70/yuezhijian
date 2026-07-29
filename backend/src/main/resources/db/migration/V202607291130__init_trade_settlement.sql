-- 需求：API-APT-010、API-TRD-002~012、结算管理-01/02/06
-- 目的：建立账单聚合、项目快照、员工分配、支付方式、结算试算及支付流水。

CREATE TABLE dbo.cat_payment_method (
    id bigint IDENTITY(1,1) NOT NULL CONSTRAINT pk_cat_payment_method PRIMARY KEY,
    method_code varchar(64) NOT NULL,
    method_name nvarchar(100) NOT NULL,
    method_type varchar(32) NOT NULL,
    is_electronic bit NOT NULL CONSTRAINT df_cat_payment_method_electronic DEFAULT (0),
    included_in_revenue bit NOT NULL CONSTRAINT df_cat_payment_method_revenue DEFAULT (1),
    needs_external_ref bit NOT NULL CONSTRAINT df_cat_payment_method_external_ref DEFAULT (0),
    status varchar(32) NOT NULL CONSTRAINT df_cat_payment_method_status DEFAULT ('ACTIVE'),
    created_at datetime2(3) NOT NULL CONSTRAINT df_cat_payment_method_created_at DEFAULT (sysdatetime()),
    updated_at datetime2(3) NOT NULL CONSTRAINT df_cat_payment_method_updated_at DEFAULT (sysdatetime()),
    row_version rowversion NOT NULL,
    CONSTRAINT uq_cat_payment_method_code UNIQUE (method_code),
    CONSTRAINT ck_cat_payment_method_type CHECK (method_type IN (
        'CASH', 'BANK_CARD', 'WECHAT', 'ALIPAY', 'MEITUAN', 'STORED_VALUE', 'OTHER'
    )),
    CONSTRAINT ck_cat_payment_method_status CHECK (status IN ('ACTIVE', 'DISABLED'))
);

CREATE TABLE dbo.cat_payment_method_store (
    id bigint IDENTITY(1,1) NOT NULL CONSTRAINT pk_cat_payment_method_store PRIMARY KEY,
    payment_method_id bigint NOT NULL,
    store_id bigint NOT NULL,
    sort_no int NOT NULL CONSTRAINT df_cat_payment_method_store_sort DEFAULT (0),
    enabled bit NOT NULL CONSTRAINT df_cat_payment_method_store_enabled DEFAULT (1),
    CONSTRAINT uq_cat_payment_method_store UNIQUE (payment_method_id, store_id),
    CONSTRAINT fk_cat_payment_method_store_method FOREIGN KEY (payment_method_id) REFERENCES dbo.cat_payment_method(id),
    CONSTRAINT fk_cat_payment_method_store_store FOREIGN KEY (store_id) REFERENCES dbo.org_store(id)
);

CREATE TABLE dbo.trd_bill (
    id bigint IDENTITY(1,1) NOT NULL CONSTRAINT pk_trd_bill PRIMARY KEY,
    bill_no varchar(32) NOT NULL,
    appointment_id bigint NULL,
    member_id bigint NULL,
    guest_name nvarchar(100) NULL,
    mobile_ciphertext nvarchar(500) NULL,
    mobile_hash char(64) NULL,
    mobile_last4 char(4) NULL,
    store_id bigint NOT NULL,
    source_type varchar(32) NOT NULL,
    person_count int NOT NULL CONSTRAINT df_trd_bill_person_count DEFAULT (1),
    currency char(3) NOT NULL CONSTRAINT df_trd_bill_currency DEFAULT ('CNY'),
    original_amount decimal(19,4) NOT NULL CONSTRAINT df_trd_bill_original DEFAULT (0),
    discount_amount decimal(19,4) NOT NULL CONSTRAINT df_trd_bill_discount DEFAULT (0),
    receivable_amount decimal(19,4) NOT NULL CONSTRAINT df_trd_bill_receivable DEFAULT (0),
    received_amount decimal(19,4) NOT NULL CONSTRAINT df_trd_bill_received DEFAULT (0),
    change_amount decimal(19,4) NOT NULL CONSTRAINT df_trd_bill_change DEFAULT (0),
    status varchar(32) NOT NULL CONSTRAINT df_trd_bill_status DEFAULT ('DRAFT'),
    note nvarchar(1000) NULL,
    settled_at datetime2(3) NULL,
    cashier_id bigint NULL,
    voided_at datetime2(3) NULL,
    voided_by bigint NULL,
    void_reason_code varchar(64) NULL,
    idempotency_key varchar(128) NULL,
    created_at datetime2(3) NOT NULL CONSTRAINT df_trd_bill_created_at DEFAULT (sysdatetime()),
    created_by bigint NOT NULL,
    updated_at datetime2(3) NOT NULL CONSTRAINT df_trd_bill_updated_at DEFAULT (sysdatetime()),
    updated_by bigint NOT NULL,
    row_version rowversion NOT NULL,
    CONSTRAINT uq_trd_bill_no UNIQUE (bill_no),
    CONSTRAINT uq_trd_bill_appointment UNIQUE (appointment_id),
    CONSTRAINT fk_trd_bill_appointment FOREIGN KEY (appointment_id) REFERENCES dbo.apt_appointment(id),
    CONSTRAINT fk_trd_bill_member FOREIGN KEY (member_id) REFERENCES dbo.mem_member(id),
    CONSTRAINT fk_trd_bill_store FOREIGN KEY (store_id) REFERENCES dbo.org_store(id),
    CONSTRAINT fk_trd_bill_cashier FOREIGN KEY (cashier_id) REFERENCES dbo.iam_user(id),
    CONSTRAINT ck_trd_bill_customer CHECK (member_id IS NOT NULL OR guest_name IS NOT NULL OR appointment_id IS NOT NULL),
    CONSTRAINT ck_trd_bill_person_count CHECK (person_count BETWEEN 1 AND 100),
    CONSTRAINT ck_trd_bill_source CHECK (source_type IN ('PC', 'MOBILE', 'APPOINTMENT', 'HOME_SERVICE', 'IMPORT')),
    CONSTRAINT ck_trd_bill_amount CHECK (
        original_amount >= 0 AND discount_amount >= 0 AND receivable_amount >= 0
        AND received_amount >= 0 AND change_amount >= 0
        AND receivable_amount = original_amount - discount_amount
    ),
    CONSTRAINT ck_trd_bill_status CHECK (status IN (
        'DRAFT', 'PENDING_PAYMENT', 'SETTLED', 'VOIDED', 'ADJUSTED', 'REVERSED'
    ))
);

CREATE UNIQUE INDEX ux_trd_bill_idempotency
    ON dbo.trd_bill (idempotency_key) WHERE idempotency_key IS NOT NULL;
CREATE INDEX ix_trd_bill_store_created
    ON dbo.trd_bill (store_id, created_at DESC, status) INCLUDE (bill_no, member_id, receivable_amount);

CREATE TABLE dbo.trd_bill_line (
    id bigint IDENTITY(1,1) NOT NULL CONSTRAINT pk_trd_bill_line PRIMARY KEY,
    bill_id bigint NOT NULL,
    line_no int NOT NULL,
    item_type varchar(16) NOT NULL,
    item_id bigint NOT NULL,
    item_code_snapshot varchar(64) NOT NULL,
    item_name_snapshot nvarchar(200) NOT NULL,
    unit_price decimal(19,4) NOT NULL,
    quantity decimal(19,4) NOT NULL,
    original_amount decimal(19,4) NOT NULL,
    discount_amount decimal(19,4) NOT NULL CONSTRAINT df_trd_bill_line_discount DEFAULT (0),
    receivable_amount decimal(19,4) NOT NULL,
    actual_amount decimal(19,4) NOT NULL CONSTRAINT df_trd_bill_line_actual DEFAULT (0),
    commission_base decimal(19,4) NOT NULL CONSTRAINT df_trd_bill_line_commission DEFAULT (0),
    note nvarchar(500) NULL,
    created_at datetime2(3) NOT NULL CONSTRAINT df_trd_bill_line_created_at DEFAULT (sysdatetime()),
    CONSTRAINT uq_trd_bill_line_no UNIQUE (bill_id, line_no),
    CONSTRAINT fk_trd_bill_line_bill FOREIGN KEY (bill_id) REFERENCES dbo.trd_bill(id),
    CONSTRAINT ck_trd_bill_line_type CHECK (item_type IN ('SERVICE', 'PRODUCT', 'CARD')),
    CONSTRAINT ck_trd_bill_line_amount CHECK (
        unit_price >= 0 AND quantity > 0 AND original_amount >= 0
        AND discount_amount >= 0 AND receivable_amount >= 0
        AND receivable_amount = original_amount - discount_amount
    )
);

CREATE TABLE dbo.trd_bill_line_employee (
    id bigint IDENTITY(1,1) NOT NULL CONSTRAINT pk_trd_bill_line_employee PRIMARY KEY,
    bill_line_id bigint NOT NULL,
    employee_id bigint NOT NULL,
    employee_role varchar(32) NOT NULL,
    performance_store_id bigint NOT NULL,
    allocation_rate decimal(9,6) NOT NULL CONSTRAINT df_trd_bill_line_employee_rate DEFAULT (1),
    performance_amount decimal(19,4) NOT NULL,
    CONSTRAINT uq_trd_bill_line_employee UNIQUE (bill_line_id, employee_id, employee_role),
    CONSTRAINT fk_trd_bill_line_employee_line FOREIGN KEY (bill_line_id) REFERENCES dbo.trd_bill_line(id),
    CONSTRAINT fk_trd_bill_line_employee_employee FOREIGN KEY (employee_id) REFERENCES dbo.org_employee(id),
    CONSTRAINT fk_trd_bill_line_employee_store FOREIGN KEY (performance_store_id) REFERENCES dbo.org_store(id),
    CONSTRAINT ck_trd_bill_line_employee_role CHECK (employee_role IN ('SERVICE', 'SALES')),
    CONSTRAINT ck_trd_bill_line_employee_rate CHECK (allocation_rate > 0 AND allocation_rate <= 1),
    CONSTRAINT ck_trd_bill_line_employee_amount CHECK (performance_amount >= 0)
);

CREATE TABLE dbo.trd_settlement_quote (
    id bigint IDENTITY(1,1) NOT NULL CONSTRAINT pk_trd_settlement_quote PRIMARY KEY,
    quote_no varchar(32) NOT NULL,
    bill_id bigint NOT NULL,
    bill_row_version varbinary(8) NOT NULL,
    receivable_amount decimal(19,4) NOT NULL,
    payment_total decimal(19,4) NOT NULL,
    change_amount decimal(19,4) NOT NULL,
    difference_amount decimal(19,4) NOT NULL,
    request_json nvarchar(max) NOT NULL,
    expires_at datetime2(3) NOT NULL,
    used_at datetime2(3) NULL,
    created_at datetime2(3) NOT NULL CONSTRAINT df_trd_settlement_quote_created_at DEFAULT (sysdatetime()),
    created_by bigint NOT NULL,
    CONSTRAINT uq_trd_settlement_quote_no UNIQUE (quote_no),
    CONSTRAINT fk_trd_settlement_quote_bill FOREIGN KEY (bill_id) REFERENCES dbo.trd_bill(id),
    CONSTRAINT ck_trd_settlement_quote_amount CHECK (
        receivable_amount >= 0 AND payment_total >= 0 AND change_amount >= 0 AND difference_amount >= 0
    )
);

CREATE INDEX ix_trd_settlement_quote_bill
    ON dbo.trd_settlement_quote (bill_id, expires_at DESC);

CREATE TABLE dbo.trd_settlement_quote_payment (
    id bigint IDENTITY(1,1) NOT NULL CONSTRAINT pk_trd_settlement_quote_payment PRIMARY KEY,
    quote_id bigint NOT NULL,
    payment_method_id bigint NOT NULL,
    payment_method_code varchar(64) NOT NULL,
    payment_method_name nvarchar(100) NOT NULL,
    amount decimal(19,4) NOT NULL,
    external_reference varchar(128) NULL,
    sort_no int NOT NULL,
    CONSTRAINT fk_trd_quote_payment_quote FOREIGN KEY (quote_id) REFERENCES dbo.trd_settlement_quote(id),
    CONSTRAINT fk_trd_quote_payment_method FOREIGN KEY (payment_method_id) REFERENCES dbo.cat_payment_method(id),
    CONSTRAINT uq_trd_quote_payment_sort UNIQUE (quote_id, sort_no),
    CONSTRAINT ck_trd_quote_payment_amount CHECK (amount > 0)
);

CREATE TABLE dbo.trd_payment (
    id bigint IDENTITY(1,1) NOT NULL CONSTRAINT pk_trd_payment PRIMARY KEY,
    payment_no varchar(32) NOT NULL,
    bill_id bigint NOT NULL,
    payment_method_id bigint NOT NULL,
    amount decimal(19,4) NOT NULL,
    payment_status varchar(32) NOT NULL CONSTRAINT df_trd_payment_status DEFAULT ('SUCCESS'),
    external_order_no varchar(128) NULL,
    authorization_no varchar(128) NULL,
    paid_at datetime2(3) NOT NULL CONSTRAINT df_trd_payment_paid_at DEFAULT (sysdatetime()),
    idempotency_key varchar(128) NOT NULL,
    created_by bigint NOT NULL,
    CONSTRAINT uq_trd_payment_no UNIQUE (payment_no),
    CONSTRAINT uq_trd_payment_idempotency UNIQUE (idempotency_key),
    CONSTRAINT fk_trd_payment_bill FOREIGN KEY (bill_id) REFERENCES dbo.trd_bill(id),
    CONSTRAINT fk_trd_payment_method FOREIGN KEY (payment_method_id) REFERENCES dbo.cat_payment_method(id),
    CONSTRAINT ck_trd_payment_amount CHECK (amount > 0),
    CONSTRAINT ck_trd_payment_status CHECK (payment_status IN ('PENDING', 'SUCCESS', 'FAILED', 'REFUNDED'))
);

CREATE TABLE dbo.trd_bill_status_history (
    id bigint IDENTITY(1,1) NOT NULL CONSTRAINT pk_trd_bill_status_history PRIMARY KEY,
    bill_id bigint NOT NULL,
    from_status varchar(32) NULL,
    to_status varchar(32) NOT NULL,
    reason_code varchar(64) NULL,
    note nvarchar(500) NULL,
    occurred_at datetime2(3) NOT NULL CONSTRAINT df_trd_bill_history_time DEFAULT (sysdatetime()),
    operator_id bigint NOT NULL,
    CONSTRAINT fk_trd_bill_history_bill FOREIGN KEY (bill_id) REFERENCES dbo.trd_bill(id),
    CONSTRAINT fk_trd_bill_history_operator FOREIGN KEY (operator_id) REFERENCES dbo.iam_user(id)
);

ALTER TABLE dbo.apt_appointment
    ADD CONSTRAINT fk_apt_appointment_bill FOREIGN KEY (bill_id) REFERENCES dbo.trd_bill(id);

INSERT INTO dbo.cat_payment_method (
    method_code, method_name, method_type, is_electronic, included_in_revenue, needs_external_ref
)
VALUES
    ('CASH', N'现金', 'CASH', 0, 1, 0),
    ('BANK_CARD', N'银行卡', 'BANK_CARD', 1, 1, 0),
    ('WECHAT', N'微信支付', 'WECHAT', 1, 1, 1),
    ('ALIPAY', N'支付宝', 'ALIPAY', 1, 1, 1),
    ('MEITUAN', N'美团核销', 'MEITUAN', 1, 1, 1);

INSERT INTO dbo.cat_payment_method_store (payment_method_id, store_id, sort_no)
SELECT method.id, store.id,
       CASE method.method_code WHEN 'CASH' THEN 10 WHEN 'BANK_CARD' THEN 20
            WHEN 'WECHAT' THEN 30 WHEN 'ALIPAY' THEN 40 ELSE 50 END
FROM dbo.cat_payment_method method
CROSS JOIN dbo.org_store store;

INSERT INTO dbo.iam_permission (permission_code, permission_name, resource_type, api_pattern, http_method)
VALUES
    ('trade:bill:manage', N'维护账单', 'BUTTON', '/api/v1/bills/**', 'POST'),
    ('trade:bill:settle', N'结算账单', 'BUTTON', '/api/v1/bills/*/settle', 'POST');

INSERT INTO dbo.iam_role_permission (role_id, permission_id, effect)
SELECT role.id, permission.id, 'ALLOW'
FROM dbo.iam_role role
CROSS JOIN dbo.iam_permission permission
WHERE role.role_code = 'HEADQUARTERS_ADMIN'
  AND permission.permission_code IN ('trade:bill:manage', 'trade:bill:settle');

-- 验证：新增9张表、5种支付方式、2项权限，并建立预约到账单双向关联。
