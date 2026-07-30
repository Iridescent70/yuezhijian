-- 需求：API-AST-014~016、次卡管理-04
-- 目的：建立按已消费项目原价重计的退卡试算、申请、审批、退款和卡资产清零闭环。

CREATE TABLE dbo.ast_card_refund_quote (
    id bigint IDENTITY(1,1) NOT NULL CONSTRAINT pk_ast_card_refund_quote PRIMARY KEY,
    quote_no varchar(32) NOT NULL,
    member_card_id bigint NOT NULL,
    original_amount decimal(19,4) NOT NULL,
    consumed_reprice_amount decimal(19,4) NOT NULL,
    fee_amount decimal(19,4) NOT NULL,
    refund_amount decimal(19,4) NOT NULL,
    card_row_version varbinary(8) NOT NULL,
    expires_at datetime2(3) NOT NULL,
    used_at datetime2(3) NULL,
    created_at datetime2(3) NOT NULL CONSTRAINT df_ast_card_refund_quote_created DEFAULT (sysdatetime()),
    created_by bigint NOT NULL,
    CONSTRAINT uq_ast_card_refund_quote_no UNIQUE (quote_no),
    CONSTRAINT fk_ast_card_refund_quote_card FOREIGN KEY (member_card_id) REFERENCES dbo.ast_member_card(id),
    CONSTRAINT fk_ast_card_refund_quote_creator FOREIGN KEY (created_by) REFERENCES dbo.iam_user(id),
    CONSTRAINT ck_ast_card_refund_quote_amount CHECK (
        original_amount >= 0 AND consumed_reprice_amount >= 0 AND fee_amount >= 0
        AND fee_amount <= CASE WHEN original_amount > consumed_reprice_amount
            THEN original_amount - consumed_reprice_amount ELSE 0 END
        AND refund_amount = CASE WHEN original_amount > consumed_reprice_amount + fee_amount
            THEN original_amount - consumed_reprice_amount - fee_amount ELSE 0 END
    )
);

CREATE TABLE dbo.ast_card_refund_quote_item (
    id bigint IDENTITY(1,1) NOT NULL CONSTRAINT pk_ast_card_refund_quote_item PRIMARY KEY,
    quote_id bigint NOT NULL,
    card_ledger_id bigint NOT NULL,
    bill_id bigint NOT NULL,
    bill_no_snapshot varchar(32) NOT NULL,
    service_id bigint NOT NULL,
    service_name_snapshot nvarchar(200) NOT NULL,
    consumed_at datetime2(3) NOT NULL,
    original_amount decimal(19,4) NOT NULL,
    sort_no int NOT NULL,
    CONSTRAINT fk_ast_card_refund_quote_item_quote FOREIGN KEY (quote_id) REFERENCES dbo.ast_card_refund_quote(id),
    CONSTRAINT fk_ast_card_refund_quote_item_ledger FOREIGN KEY (card_ledger_id) REFERENCES dbo.ast_member_card_ledger(id),
    CONSTRAINT fk_ast_card_refund_quote_item_bill FOREIGN KEY (bill_id) REFERENCES dbo.trd_bill(id),
    CONSTRAINT fk_ast_card_refund_quote_item_service FOREIGN KEY (service_id) REFERENCES dbo.cat_service(id),
    CONSTRAINT uq_ast_card_refund_quote_item UNIQUE (quote_id, card_ledger_id),
    CONSTRAINT uq_ast_card_refund_quote_item_sort UNIQUE (quote_id, sort_no),
    CONSTRAINT ck_ast_card_refund_quote_item_amount CHECK (original_amount >= 0)
);

CREATE TABLE dbo.ast_card_refund_request (
    id bigint IDENTITY(1,1) NOT NULL CONSTRAINT pk_ast_card_refund_request PRIMARY KEY,
    request_no varchar(32) NOT NULL,
    quote_id bigint NOT NULL,
    member_card_id bigint NOT NULL,
    member_id bigint NOT NULL,
    original_amount decimal(19,4) NOT NULL,
    consumed_reprice_amount decimal(19,4) NOT NULL,
    fee_amount decimal(19,4) NOT NULL,
    refund_amount decimal(19,4) NOT NULL,
    refund_method_id bigint NULL,
    refund_method_name_snapshot nvarchar(100) NULL,
    refund_method_requires_reference bit NOT NULL CONSTRAINT df_ast_card_refund_method_ref DEFAULT (0),
    handled_store_id bigint NOT NULL,
    handled_employee_id bigint NULL,
    reason nvarchar(1000) NOT NULL,
    status varchar(32) NOT NULL CONSTRAINT df_ast_card_refund_status DEFAULT ('SUBMITTED'),
    commission_adjustment_status varchar(32) NOT NULL CONSTRAINT df_ast_card_refund_commission DEFAULT ('PENDING_MODULE'),
    card_row_version varbinary(8) NOT NULL,
    request_idempotency_key varchar(128) NOT NULL,
    execution_idempotency_key varchar(128) NULL,
    requested_at datetime2(3) NOT NULL CONSTRAINT df_ast_card_refund_requested DEFAULT (sysdatetime()),
    requested_by bigint NOT NULL,
    reviewed_at datetime2(3) NULL,
    reviewed_by bigint NULL,
    review_comment nvarchar(1000) NULL,
    executed_at datetime2(3) NULL,
    executed_by bigint NULL,
    active_flag bit NOT NULL CONSTRAINT df_ast_card_refund_active DEFAULT (1),
    row_version rowversion NOT NULL,
    CONSTRAINT uq_ast_card_refund_request_no UNIQUE (request_no),
    CONSTRAINT uq_ast_card_refund_request_quote UNIQUE (quote_id),
    CONSTRAINT uq_ast_card_refund_request_key UNIQUE (request_idempotency_key),
    CONSTRAINT uq_ast_card_refund_execution_key UNIQUE (execution_idempotency_key),
    CONSTRAINT fk_ast_card_refund_request_quote FOREIGN KEY (quote_id) REFERENCES dbo.ast_card_refund_quote(id),
    CONSTRAINT fk_ast_card_refund_request_card FOREIGN KEY (member_card_id) REFERENCES dbo.ast_member_card(id),
    CONSTRAINT fk_ast_card_refund_request_member FOREIGN KEY (member_id) REFERENCES dbo.mem_member(id),
    CONSTRAINT fk_ast_card_refund_request_method FOREIGN KEY (refund_method_id) REFERENCES dbo.cat_payment_method(id),
    CONSTRAINT fk_ast_card_refund_request_store FOREIGN KEY (handled_store_id) REFERENCES dbo.org_store(id),
    CONSTRAINT fk_ast_card_refund_request_employee FOREIGN KEY (handled_employee_id) REFERENCES dbo.org_employee(id),
    CONSTRAINT fk_ast_card_refund_request_requester FOREIGN KEY (requested_by) REFERENCES dbo.iam_user(id),
    CONSTRAINT fk_ast_card_refund_request_reviewer FOREIGN KEY (reviewed_by) REFERENCES dbo.iam_user(id),
    CONSTRAINT fk_ast_card_refund_request_executor FOREIGN KEY (executed_by) REFERENCES dbo.iam_user(id),
    CONSTRAINT ck_ast_card_refund_request_status CHECK (status IN ('SUBMITTED','APPROVED','REJECTED','EXECUTED')),
    CONSTRAINT ck_ast_card_refund_active CHECK (
        (status = 'REJECTED' AND active_flag = 0)
        OR (status IN ('SUBMITTED','APPROVED','EXECUTED') AND active_flag = 1)
    ),
    CONSTRAINT ck_ast_card_refund_commission_status CHECK (
        commission_adjustment_status IN ('PENDING_MODULE','COMPLETED','NOT_APPLICABLE')
    ),
    CONSTRAINT ck_ast_card_refund_request_method CHECK (refund_amount = 0 OR refund_method_id IS NOT NULL)
);

CREATE UNIQUE INDEX ux_ast_card_refund_active_card
    ON dbo.ast_card_refund_request (member_card_id) WHERE active_flag = 1;
CREATE INDEX ix_ast_card_refund_status_time
    ON dbo.ast_card_refund_request (status, requested_at DESC, id DESC);

CREATE TABLE dbo.ast_card_refund_payment (
    id bigint IDENTITY(1,1) NOT NULL CONSTRAINT pk_ast_card_refund_payment PRIMARY KEY,
    refund_no varchar(32) NOT NULL,
    request_id bigint NOT NULL,
    payment_method_id bigint NOT NULL,
    refund_amount decimal(19,4) NOT NULL,
    refund_status varchar(32) NOT NULL CONSTRAINT df_ast_card_refund_payment_status DEFAULT ('SUCCESS'),
    external_refund_reference varchar(128) NULL,
    idempotency_key varchar(128) NOT NULL,
    completed_at datetime2(3) NULL,
    created_by bigint NOT NULL,
    CONSTRAINT uq_ast_card_refund_payment_no UNIQUE (refund_no),
    CONSTRAINT uq_ast_card_refund_payment_request UNIQUE (request_id),
    CONSTRAINT uq_ast_card_refund_payment_key UNIQUE (idempotency_key),
    CONSTRAINT fk_ast_card_refund_payment_request FOREIGN KEY (request_id) REFERENCES dbo.ast_card_refund_request(id),
    CONSTRAINT fk_ast_card_refund_payment_method FOREIGN KEY (payment_method_id) REFERENCES dbo.cat_payment_method(id),
    CONSTRAINT fk_ast_card_refund_payment_creator FOREIGN KEY (created_by) REFERENCES dbo.iam_user(id),
    CONSTRAINT ck_ast_card_refund_payment_amount CHECK (refund_amount > 0),
    CONSTRAINT ck_ast_card_refund_payment_status CHECK (refund_status IN ('PENDING','SUCCESS','FAILED'))
);

ALTER TABLE dbo.ast_member_card_ledger DROP CONSTRAINT ck_ast_card_ledger_type;
ALTER TABLE dbo.ast_member_card_ledger ADD CONSTRAINT ck_ast_card_ledger_type CHECK (transaction_type IN (
    'PURCHASE', 'CONSUME', 'REFUND', 'EXCHANGE_OUT', 'EXCHANGE_IN',
    'TRANSFER_OUT', 'TRANSFER_IN', 'REFUND_OUT', 'ADJUST_IN', 'ADJUST_OUT', 'REVERSAL', 'MIGRATION'
));

INSERT INTO dbo.iam_permission (permission_code, permission_name, resource_type, api_pattern, http_method)
VALUES
    ('member:card:refund:view', N'查看退卡申请', 'MENU', '/api/v1/card-refund-requests/**', 'GET'),
    ('member:card:refund:manage', N'申请和执行退卡', 'BUTTON', '/api/v1/card-refund-requests/**', 'POST'),
    ('member:card:refund:approve', N'审批退卡', 'BUTTON', '/api/v1/card-refund-requests/*/review', 'POST');

INSERT INTO dbo.iam_role_permission (role_id, permission_id, effect)
SELECT role.id, permission.id, 'ALLOW'
FROM dbo.iam_role role CROSS JOIN dbo.iam_permission permission
WHERE role.role_code = 'HEADQUARTERS_ADMIN'
  AND permission.permission_code IN (
      'member:card:refund:view', 'member:card:refund:manage', 'member:card:refund:approve'
  );

INSERT INTO dbo.iam_menu (menu_code, name, route, icon, sort_no, client_type, permission_code)
VALUES ('card-refunds', N'退卡管理', '/app/assets/card-refunds', 'CreditCard', 55, 'PC', 'member:card:refund:view');

-- 验证：申请后卡冻结；驳回后恢复；审批执行后卡清零、退款落账，提成冲回状态明确为待模块处理。
