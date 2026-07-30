-- 需求：API-MEM-001~003、UI-MEM-001~003，会员首个纵向闭环
-- 目的：建立会员档案、等级、标签、会员卡和资产汇总账户。
-- 注意：手机号仅保存密文、带密钥哈希和尾号，不保存明文。
-- 恢复：上线前备份；共享环境不得执行 Flyway clean 或修改本文件。

CREATE TABLE dbo.mem_level (
    id bigint IDENTITY(1,1) NOT NULL CONSTRAINT pk_mem_level PRIMARY KEY,
    level_code varchar(64) NOT NULL,
    level_name nvarchar(100) NOT NULL,
    stored_value_threshold decimal(19,4) NOT NULL CONSTRAINT df_mem_level_threshold DEFAULT (0),
    auto_upgrade bit NOT NULL CONSTRAINT df_mem_level_auto_upgrade DEFAULT (0),
    birthday_discount_rate decimal(9,6) NOT NULL CONSTRAINT df_mem_level_birthday_rate DEFAULT (1),
    service_discount_rate decimal(9,6) NOT NULL CONSTRAINT df_mem_level_service_rate DEFAULT (1),
    status varchar(32) NOT NULL CONSTRAINT df_mem_level_status DEFAULT ('ACTIVE'),
    sort_no int NOT NULL CONSTRAINT df_mem_level_sort DEFAULT (0),
    created_at datetime2(3) NOT NULL CONSTRAINT df_mem_level_created_at DEFAULT (sysdatetime()),
    created_by bigint NULL,
    updated_at datetime2(3) NOT NULL CONSTRAINT df_mem_level_updated_at DEFAULT (sysdatetime()),
    updated_by bigint NULL,
    row_version rowversion NOT NULL,
    CONSTRAINT uq_mem_level_code UNIQUE (level_code),
    CONSTRAINT ck_mem_level_threshold CHECK (stored_value_threshold >= 0),
    CONSTRAINT ck_mem_level_rates CHECK (
        birthday_discount_rate BETWEEN 0 AND 1 AND service_discount_rate BETWEEN 0 AND 1
    ),
    CONSTRAINT ck_mem_level_status CHECK (status IN ('ACTIVE', 'DISABLED'))
);

CREATE TABLE dbo.mem_member (
    id bigint IDENTITY(1,1) NOT NULL CONSTRAINT pk_mem_member PRIMARY KEY,
    member_no varchar(32) NOT NULL,
    full_name nvarchar(100) NOT NULL,
    nickname nvarchar(100) NULL,
    gender varchar(16) NOT NULL CONSTRAINT df_mem_member_gender DEFAULT ('UNKNOWN'),
    birthday date NULL,
    mobile_ciphertext nvarchar(500) NOT NULL,
    mobile_hash char(64) NOT NULL,
    mobile_last4 char(4) NOT NULL,
    email nvarchar(255) NULL,
    source_type varchar(32) NOT NULL CONSTRAINT df_mem_member_source DEFAULT ('MANUAL'),
    join_store_id bigint NOT NULL,
    owner_store_id bigint NOT NULL,
    advisor_employee_id bigint NULL,
    level_id bigint NULL,
    frozen_at datetime2(3) NULL,
    freeze_reason nvarchar(500) NULL,
    special_flag bit NOT NULL CONSTRAINT df_mem_member_special DEFAULT (0),
    last_visit_at datetime2(3) NULL,
    status varchar(32) NOT NULL CONSTRAINT df_mem_member_status DEFAULT ('ACTIVE'),
    created_at datetime2(3) NOT NULL CONSTRAINT df_mem_member_created_at DEFAULT (sysdatetime()),
    created_by bigint NULL,
    updated_at datetime2(3) NOT NULL CONSTRAINT df_mem_member_updated_at DEFAULT (sysdatetime()),
    updated_by bigint NULL,
    row_version rowversion NOT NULL,
    CONSTRAINT uq_mem_member_no UNIQUE (member_no),
    CONSTRAINT fk_mem_member_join_store FOREIGN KEY (join_store_id) REFERENCES dbo.org_store(id),
    CONSTRAINT fk_mem_member_owner_store FOREIGN KEY (owner_store_id) REFERENCES dbo.org_store(id),
    CONSTRAINT fk_mem_member_advisor FOREIGN KEY (advisor_employee_id) REFERENCES dbo.org_employee(id),
    CONSTRAINT fk_mem_member_level FOREIGN KEY (level_id) REFERENCES dbo.mem_level(id),
    CONSTRAINT ck_mem_member_gender CHECK (gender IN ('UNKNOWN', 'FEMALE', 'MALE', 'OTHER')),
    CONSTRAINT ck_mem_member_status CHECK (status IN ('ACTIVE', 'FROZEN', 'INACTIVE')),
    CONSTRAINT ck_mem_member_freeze CHECK (
        (status = 'FROZEN' AND frozen_at IS NOT NULL) OR (status <> 'FROZEN')
    )
);

CREATE UNIQUE INDEX ux_mem_member_mobile_hash
    ON dbo.mem_member (mobile_hash);
CREATE INDEX ix_mem_member_owner_status
    ON dbo.mem_member (owner_store_id, status, id DESC);
CREATE INDEX ix_mem_member_name
    ON dbo.mem_member (full_name, id DESC);
CREATE INDEX ix_mem_member_last_visit
    ON dbo.mem_member (last_visit_at DESC) WHERE last_visit_at IS NOT NULL;

CREATE TABLE dbo.mem_membership_card (
    id bigint IDENTITY(1,1) NOT NULL CONSTRAINT pk_mem_membership_card PRIMARY KEY,
    member_id bigint NOT NULL,
    card_no varchar(64) NOT NULL,
    password_hash varchar(255) NULL,
    register_store_id bigint NOT NULL,
    register_user_id bigint NULL,
    registered_at datetime2(3) NOT NULL CONSTRAINT df_mem_card_registered DEFAULT (sysdatetime()),
    revoke_store_id bigint NULL,
    revoke_user_id bigint NULL,
    revoked_at datetime2(3) NULL,
    revoke_reason nvarchar(500) NULL,
    previous_card_id bigint NULL,
    status varchar(32) NOT NULL CONSTRAINT df_mem_card_status DEFAULT ('ACTIVE'),
    created_at datetime2(3) NOT NULL CONSTRAINT df_mem_card_created_at DEFAULT (sysdatetime()),
    row_version rowversion NOT NULL,
    CONSTRAINT uq_mem_membership_card_no UNIQUE (card_no),
    CONSTRAINT fk_mem_card_member FOREIGN KEY (member_id) REFERENCES dbo.mem_member(id),
    CONSTRAINT fk_mem_card_register_store FOREIGN KEY (register_store_id) REFERENCES dbo.org_store(id),
    CONSTRAINT fk_mem_card_register_user FOREIGN KEY (register_user_id) REFERENCES dbo.iam_user(id),
    CONSTRAINT fk_mem_card_revoke_store FOREIGN KEY (revoke_store_id) REFERENCES dbo.org_store(id),
    CONSTRAINT fk_mem_card_revoke_user FOREIGN KEY (revoke_user_id) REFERENCES dbo.iam_user(id),
    CONSTRAINT fk_mem_card_previous FOREIGN KEY (previous_card_id) REFERENCES dbo.mem_membership_card(id),
    CONSTRAINT ck_mem_card_status CHECK (status IN ('ACTIVE', 'REVOKED', 'REPLACED'))
);

CREATE INDEX ix_mem_card_member_status
    ON dbo.mem_membership_card (member_id, status, registered_at DESC);

CREATE TABLE dbo.mem_tag (
    id bigint IDENTITY(1,1) NOT NULL CONSTRAINT pk_mem_tag PRIMARY KEY,
    tag_code varchar(64) NOT NULL,
    tag_name nvarchar(100) NOT NULL,
    tag_source varchar(16) NOT NULL,
    rule_json nvarchar(max) NULL,
    color varchar(16) NULL,
    negative_flag bit NOT NULL CONSTRAINT df_mem_tag_negative DEFAULT (0),
    status varchar(32) NOT NULL CONSTRAINT df_mem_tag_status DEFAULT ('ACTIVE'),
    created_at datetime2(3) NOT NULL CONSTRAINT df_mem_tag_created_at DEFAULT (sysdatetime()),
    created_by bigint NULL,
    updated_at datetime2(3) NOT NULL CONSTRAINT df_mem_tag_updated_at DEFAULT (sysdatetime()),
    updated_by bigint NULL,
    row_version rowversion NOT NULL,
    CONSTRAINT uq_mem_tag_code UNIQUE (tag_code),
    CONSTRAINT ck_mem_tag_source CHECK (tag_source IN ('MANUAL', 'RULE', 'AI')),
    CONSTRAINT ck_mem_tag_status CHECK (status IN ('ACTIVE', 'DISABLED'))
);

CREATE TABLE dbo.mem_member_tag (
    id bigint IDENTITY(1,1) NOT NULL CONSTRAINT pk_mem_member_tag PRIMARY KEY,
    member_id bigint NOT NULL,
    tag_id bigint NOT NULL,
    source varchar(16) NOT NULL,
    confidence decimal(5,4) NULL,
    evidence_json nvarchar(max) NULL,
    assigned_at datetime2(3) NOT NULL CONSTRAINT df_mem_member_tag_assigned DEFAULT (sysdatetime()),
    assigned_by bigint NULL,
    removed_at datetime2(3) NULL,
    removed_by bigint NULL,
    CONSTRAINT fk_mem_member_tag_member FOREIGN KEY (member_id) REFERENCES dbo.mem_member(id),
    CONSTRAINT fk_mem_member_tag_tag FOREIGN KEY (tag_id) REFERENCES dbo.mem_tag(id),
    CONSTRAINT fk_mem_member_tag_assigner FOREIGN KEY (assigned_by) REFERENCES dbo.iam_user(id),
    CONSTRAINT fk_mem_member_tag_remover FOREIGN KEY (removed_by) REFERENCES dbo.iam_user(id),
    CONSTRAINT ck_mem_member_tag_source CHECK (source IN ('MANUAL', 'RULE', 'AI')),
    CONSTRAINT ck_mem_member_tag_confidence CHECK (confidence IS NULL OR confidence BETWEEN 0 AND 1)
);

CREATE UNIQUE INDEX ux_mem_member_tag_active
    ON dbo.mem_member_tag (member_id, tag_id) WHERE removed_at IS NULL;
CREATE INDEX ix_mem_member_tag_tag
    ON dbo.mem_member_tag (tag_id, member_id) WHERE removed_at IS NULL;

CREATE TABLE dbo.ast_balance_account (
    id bigint IDENTITY(1,1) NOT NULL CONSTRAINT pk_ast_balance_account PRIMARY KEY,
    member_id bigint NOT NULL,
    available_balance decimal(19,4) NOT NULL CONSTRAINT df_ast_balance_available DEFAULT (0),
    frozen_balance decimal(19,4) NOT NULL CONSTRAINT df_ast_balance_frozen DEFAULT (0),
    total_recharged decimal(19,4) NOT NULL CONSTRAINT df_ast_balance_recharged DEFAULT (0),
    last_transaction_at datetime2(3) NULL,
    created_at datetime2(3) NOT NULL CONSTRAINT df_ast_balance_created_at DEFAULT (sysdatetime()),
    updated_at datetime2(3) NOT NULL CONSTRAINT df_ast_balance_updated_at DEFAULT (sysdatetime()),
    row_version rowversion NOT NULL,
    CONSTRAINT uq_ast_balance_member UNIQUE (member_id),
    CONSTRAINT fk_ast_balance_member FOREIGN KEY (member_id) REFERENCES dbo.mem_member(id),
    CONSTRAINT ck_ast_balance_nonnegative CHECK (available_balance >= 0 AND frozen_balance >= 0 AND total_recharged >= 0)
);

CREATE TABLE dbo.ast_point_account (
    id bigint IDENTITY(1,1) NOT NULL CONSTRAINT pk_ast_point_account PRIMARY KEY,
    member_id bigint NOT NULL,
    available_points int NOT NULL CONSTRAINT df_ast_point_available DEFAULT (0),
    lifetime_points int NOT NULL CONSTRAINT df_ast_point_lifetime DEFAULT (0),
    last_transaction_at datetime2(3) NULL,
    created_at datetime2(3) NOT NULL CONSTRAINT df_ast_point_created_at DEFAULT (sysdatetime()),
    updated_at datetime2(3) NOT NULL CONSTRAINT df_ast_point_updated_at DEFAULT (sysdatetime()),
    row_version rowversion NOT NULL,
    CONSTRAINT uq_ast_point_member UNIQUE (member_id),
    CONSTRAINT fk_ast_point_member FOREIGN KEY (member_id) REFERENCES dbo.mem_member(id),
    CONSTRAINT ck_ast_point_nonnegative CHECK (available_points >= 0 AND lifetime_points >= available_points)
);

INSERT INTO dbo.mem_level (
    level_code, level_name, stored_value_threshold, auto_upgrade,
    birthday_discount_rate, service_discount_rate, sort_no
)
VALUES ('STANDARD', N'普通会员', 0, 0, 1, 1, 10);

INSERT INTO dbo.mem_tag (tag_code, tag_name, tag_source, color, negative_flag)
VALUES
    ('NEW_MEMBER', N'新会员', 'RULE', '#8f5267', 0),
    ('HIGH_VALUE', N'高价值会员', 'RULE', '#c17b32', 0),
    ('FOLLOW_UP', N'需要跟进', 'MANUAL', '#d14b4b', 1);

-- 验证 SQL：预期表 7、等级 1、标签 3。
-- SELECT COUNT(*) table_count FROM sys.tables WHERE name IN
-- ('mem_level','mem_member','mem_membership_card','mem_tag','mem_member_tag','ast_balance_account','ast_point_account');
-- SELECT COUNT(*) level_count FROM dbo.mem_level;
-- SELECT COUNT(*) tag_count FROM dbo.mem_tag;
