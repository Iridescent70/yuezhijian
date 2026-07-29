-- 需求：API-TRD-017~020、结算管理-04
-- 目的：建立整单冲销申请、审批、执行及支付退款事实。

CREATE TABLE dbo.trd_reversal (
    id bigint IDENTITY(1,1) NOT NULL CONSTRAINT pk_trd_reversal PRIMARY KEY,
    reversal_no varchar(32) NOT NULL,
    bill_id bigint NOT NULL,
    refund_amount decimal(19,4) NOT NULL,
    reason nvarchar(1000) NOT NULL,
    status varchar(32) NOT NULL CONSTRAINT df_trd_reversal_status DEFAULT ('SUBMITTED'),
    request_idempotency_key varchar(128) NOT NULL,
    execution_idempotency_key varchar(128) NULL,
    requested_at datetime2(3) NOT NULL CONSTRAINT df_trd_reversal_requested DEFAULT (sysdatetime()),
    requested_by bigint NOT NULL,
    reviewed_at datetime2(3) NULL,
    reviewed_by bigint NULL,
    review_comment nvarchar(1000) NULL,
    executed_at datetime2(3) NULL,
    executed_by bigint NULL,
    active_bill_id AS (CASE WHEN status = 'REJECTED' THEN NULL ELSE bill_id END) PERSISTED,
    row_version rowversion NOT NULL,
    CONSTRAINT uq_trd_reversal_no UNIQUE (reversal_no),
    CONSTRAINT uq_trd_reversal_request_key UNIQUE (request_idempotency_key),
    CONSTRAINT uq_trd_reversal_execution_key UNIQUE (execution_idempotency_key),
    CONSTRAINT fk_trd_reversal_bill FOREIGN KEY (bill_id) REFERENCES dbo.trd_bill(id),
    CONSTRAINT fk_trd_reversal_requester FOREIGN KEY (requested_by) REFERENCES dbo.iam_user(id),
    CONSTRAINT fk_trd_reversal_reviewer FOREIGN KEY (reviewed_by) REFERENCES dbo.iam_user(id),
    CONSTRAINT fk_trd_reversal_executor FOREIGN KEY (executed_by) REFERENCES dbo.iam_user(id),
    CONSTRAINT ck_trd_reversal_amount CHECK (refund_amount > 0),
    CONSTRAINT ck_trd_reversal_status CHECK (status IN ('SUBMITTED', 'APPROVED', 'REJECTED', 'EXECUTED'))
);

CREATE UNIQUE INDEX ux_trd_reversal_active_bill
    ON dbo.trd_reversal (active_bill_id) WHERE active_bill_id IS NOT NULL;
CREATE INDEX ix_trd_reversal_status_time
    ON dbo.trd_reversal (status, requested_at DESC, id DESC);

CREATE UNIQUE INDEX ux_ast_balance_ledger_reversed
    ON dbo.ast_balance_ledger (reversed_ledger_id) WHERE reversed_ledger_id IS NOT NULL;
CREATE UNIQUE INDEX ux_ast_point_ledger_reversed
    ON dbo.ast_point_ledger (reversed_ledger_id) WHERE reversed_ledger_id IS NOT NULL;
CREATE UNIQUE INDEX ux_ast_card_ledger_reversed
    ON dbo.ast_member_card_ledger (reversed_ledger_id) WHERE reversed_ledger_id IS NOT NULL;

CREATE TABLE dbo.trd_payment_refund (
    id bigint IDENTITY(1,1) NOT NULL CONSTRAINT pk_trd_payment_refund PRIMARY KEY,
    refund_no varchar(32) NOT NULL,
    reversal_id bigint NOT NULL,
    payment_id bigint NOT NULL,
    refund_amount decimal(19,4) NOT NULL,
    refund_status varchar(32) NOT NULL CONSTRAINT df_trd_refund_status DEFAULT ('SUCCESS'),
    external_refund_no varchar(128) NULL,
    requested_at datetime2(3) NOT NULL CONSTRAINT df_trd_refund_requested DEFAULT (sysdatetime()),
    completed_at datetime2(3) NULL,
    idempotency_key varchar(128) NOT NULL,
    created_by bigint NOT NULL,
    CONSTRAINT uq_trd_refund_no UNIQUE (refund_no),
    CONSTRAINT uq_trd_refund_payment UNIQUE (reversal_id, payment_id),
    CONSTRAINT uq_trd_refund_idempotency UNIQUE (idempotency_key),
    CONSTRAINT fk_trd_refund_reversal FOREIGN KEY (reversal_id) REFERENCES dbo.trd_reversal(id),
    CONSTRAINT fk_trd_refund_payment FOREIGN KEY (payment_id) REFERENCES dbo.trd_payment(id),
    CONSTRAINT fk_trd_refund_creator FOREIGN KEY (created_by) REFERENCES dbo.iam_user(id),
    CONSTRAINT ck_trd_refund_amount CHECK (refund_amount > 0),
    CONSTRAINT ck_trd_refund_status CHECK (refund_status IN ('PENDING', 'SUCCESS', 'FAILED'))
);

INSERT INTO dbo.iam_permission (permission_code, permission_name, resource_type, api_pattern, http_method)
VALUES
    ('trade:reversal:view', N'查看冲销', 'MENU', '/api/v1/reversals/**', 'GET'),
    ('trade:reversal:manage', N'申请和执行冲销', 'BUTTON', '/api/v1/reversals/**', 'POST'),
    ('trade:reversal:approve', N'审批冲销', 'BUTTON', '/api/v1/reversals/*/review', 'POST');

INSERT INTO dbo.iam_role_permission (role_id, permission_id, effect)
SELECT role.id, permission.id, 'ALLOW'
FROM dbo.iam_role role CROSS JOIN dbo.iam_permission permission
WHERE role.role_code = 'HEADQUARTERS_ADMIN'
  AND permission.permission_code IN ('trade:reversal:view', 'trade:reversal:manage', 'trade:reversal:approve');

INSERT INTO dbo.iam_menu (menu_code, name, route, icon, sort_no, client_type, permission_code)
VALUES ('reversals', N'冲销管理', '/app/settlement/reversals', 'RefreshLeft', 50, 'PC', 'trade:reversal:view');

-- 验证：冲销必须先审批再执行；同一账单只能有一条有效冲销；同一原资产流水只能反向一次。
