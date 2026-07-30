-- 需求：API-AST-013、次卡管理-03
-- 目的：正常有效次卡转赠时关闭原卡，为接收会员建立剩余资产卡并保留双方流水。

CREATE TABLE dbo.ast_card_transfer (
    id bigint IDENTITY(1,1) NOT NULL CONSTRAINT pk_ast_card_transfer PRIMARY KEY,
    transfer_no varchar(32) NOT NULL,
    source_card_id bigint NOT NULL,
    target_card_id bigint NOT NULL,
    source_member_id bigint NOT NULL,
    recipient_member_id bigint NOT NULL,
    remaining_times decimal(19,4) NOT NULL,
    remaining_value decimal(19,4) NOT NULL,
    old_expires_at datetime2(3) NOT NULL,
    new_expires_at datetime2(3) NOT NULL,
    reason nvarchar(500) NOT NULL,
    handled_store_id bigint NOT NULL,
    handled_employee_id bigint NULL,
    idempotency_key varchar(128) NOT NULL,
    executed_at datetime2(3) NOT NULL CONSTRAINT df_ast_card_transfer_executed DEFAULT (sysdatetime()),
    created_by bigint NOT NULL,
    CONSTRAINT uq_ast_card_transfer_no UNIQUE (transfer_no),
    CONSTRAINT uq_ast_card_transfer_source UNIQUE (source_card_id),
    CONSTRAINT uq_ast_card_transfer_target UNIQUE (target_card_id),
    CONSTRAINT uq_ast_card_transfer_idempotency UNIQUE (idempotency_key),
    CONSTRAINT fk_ast_card_transfer_source_card FOREIGN KEY (source_card_id) REFERENCES dbo.ast_member_card(id),
    CONSTRAINT fk_ast_card_transfer_target_card FOREIGN KEY (target_card_id) REFERENCES dbo.ast_member_card(id),
    CONSTRAINT fk_ast_card_transfer_source_member FOREIGN KEY (source_member_id) REFERENCES dbo.mem_member(id),
    CONSTRAINT fk_ast_card_transfer_recipient FOREIGN KEY (recipient_member_id) REFERENCES dbo.mem_member(id),
    CONSTRAINT fk_ast_card_transfer_store FOREIGN KEY (handled_store_id) REFERENCES dbo.org_store(id),
    CONSTRAINT fk_ast_card_transfer_employee FOREIGN KEY (handled_employee_id) REFERENCES dbo.org_employee(id),
    CONSTRAINT fk_ast_card_transfer_creator FOREIGN KEY (created_by) REFERENCES dbo.iam_user(id),
    CONSTRAINT ck_ast_card_transfer_member CHECK (source_member_id <> recipient_member_id),
    CONSTRAINT ck_ast_card_transfer_asset CHECK (remaining_times > 0 AND remaining_value >= 0),
    CONSTRAINT ck_ast_card_transfer_expiry CHECK (new_expires_at > executed_at)
);

CREATE INDEX ix_ast_card_transfer_member_time
    ON dbo.ast_card_transfer (recipient_member_id, executed_at DESC, id DESC);

-- 验证：原卡必须变为TRANSFERRED且余次清零；接收会员新卡继承原卡剩余项目、折算价值和指定有效期。
