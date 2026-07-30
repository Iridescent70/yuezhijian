/*
 * P1 平台模型：启用芋道会员基础表，并建立悦指间门店、员工、会员主档和迁移审计表。
 * 本脚本只允许在由芋道 SQL Server 基线初始化的新库中执行，旧库始终保持只读。
 */

IF OBJECT_ID(N'dbo.yuezhijian_schema_baseline', N'U') IS NULL
    THROW 50001, 'Missing Yuezhijian baseline marker. Refuse to migrate an unknown database.', 1;

IF OBJECT_ID(N'dbo.member_user', N'U') IS NOT NULL
    THROW 50002, 'member_user already exists. Refuse to overwrite an existing member schema.', 1;

CREATE TABLE dbo.member_user
(
    id bigint IDENTITY(1,1) NOT NULL CONSTRAINT pk_member_user PRIMARY KEY,
    mobile nvarchar(256) NULL,
    mobile_hash char(64) NULL,
    mobile_last4 char(4) NULL,
    email nvarchar(50) NULL,
    password nvarchar(100) NOT NULL CONSTRAINT df_member_user_password DEFAULT N'',
    status tinyint NOT NULL CONSTRAINT df_member_user_status DEFAULT 0,
    register_ip nvarchar(50) NOT NULL,
    register_terminal int NOT NULL CONSTRAINT df_member_user_terminal DEFAULT 0,
    login_ip nvarchar(50) NULL,
    login_date datetime2 NULL,
    nickname nvarchar(30) NOT NULL,
    avatar nvarchar(512) NOT NULL CONSTRAINT df_member_user_avatar DEFAULT N'',
    name nvarchar(30) NULL,
    sex tinyint NULL,
    birthday datetime2 NULL,
    area_id int NULL,
    mark nvarchar(255) NULL,
    point int NOT NULL CONSTRAINT df_member_user_point DEFAULT 0,
    tag_ids nvarchar(255) NULL,
    level_id bigint NULL,
    experience int NOT NULL CONSTRAINT df_member_user_experience DEFAULT 0,
    group_id bigint NULL,
    creator nvarchar(64) NULL CONSTRAINT df_member_user_creator DEFAULT N'',
    create_time datetime2 NOT NULL CONSTRAINT df_member_user_create_time DEFAULT CURRENT_TIMESTAMP,
    updater nvarchar(64) NULL CONSTRAINT df_member_user_updater DEFAULT N'',
    update_time datetime2 NOT NULL CONSTRAINT df_member_user_update_time DEFAULT CURRENT_TIMESTAMP,
    deleted bit NOT NULL CONSTRAINT df_member_user_deleted DEFAULT 0,
    tenant_id bigint NOT NULL CONSTRAINT df_member_user_tenant DEFAULT 1
);

CREATE UNIQUE INDEX uk_member_user_mobile_hash
    ON dbo.member_user (mobile_hash) WHERE deleted = 0 AND mobile_hash IS NOT NULL;
CREATE UNIQUE INDEX uk_member_user_email
    ON dbo.member_user (email) WHERE deleted = 0 AND email IS NOT NULL;
CREATE INDEX idx_member_user_nickname ON dbo.member_user (nickname);

CREATE TABLE dbo.member_address
(
    id bigint IDENTITY(1,1) NOT NULL CONSTRAINT pk_member_address PRIMARY KEY,
    user_id bigint NOT NULL,
    name nvarchar(30) NOT NULL,
    mobile nvarchar(256) NOT NULL,
    area_id bigint NOT NULL,
    detail_address nvarchar(250) NOT NULL,
    default_status bit NOT NULL CONSTRAINT df_member_address_default DEFAULT 0,
    creator nvarchar(64) NULL CONSTRAINT df_member_address_creator DEFAULT N'',
    create_time datetime2 NOT NULL CONSTRAINT df_member_address_create_time DEFAULT CURRENT_TIMESTAMP,
    updater nvarchar(64) NULL CONSTRAINT df_member_address_updater DEFAULT N'',
    update_time datetime2 NOT NULL CONSTRAINT df_member_address_update_time DEFAULT CURRENT_TIMESTAMP,
    deleted bit NOT NULL CONSTRAINT df_member_address_deleted DEFAULT 0,
    CONSTRAINT fk_member_address_user FOREIGN KEY (user_id) REFERENCES dbo.member_user(id)
);
CREATE INDEX idx_member_address_user ON dbo.member_address (user_id, deleted);

CREATE TABLE dbo.member_tag
(
    id bigint IDENTITY(1,1) NOT NULL CONSTRAINT pk_member_tag PRIMARY KEY,
    name nvarchar(64) NOT NULL,
    creator nvarchar(64) NULL CONSTRAINT df_member_tag_creator DEFAULT N'',
    create_time datetime2 NOT NULL CONSTRAINT df_member_tag_create_time DEFAULT CURRENT_TIMESTAMP,
    updater nvarchar(64) NULL CONSTRAINT df_member_tag_updater DEFAULT N'',
    update_time datetime2 NOT NULL CONSTRAINT df_member_tag_update_time DEFAULT CURRENT_TIMESTAMP,
    deleted bit NOT NULL CONSTRAINT df_member_tag_deleted DEFAULT 0,
    tenant_id bigint NOT NULL CONSTRAINT df_member_tag_tenant DEFAULT 1
);
CREATE UNIQUE INDEX uk_member_tag_name ON dbo.member_tag (tenant_id, name) WHERE deleted = 0;

CREATE TABLE dbo.member_level
(
    id bigint IDENTITY(1,1) NOT NULL CONSTRAINT pk_member_level PRIMARY KEY,
    name nvarchar(64) NOT NULL,
    level int NOT NULL,
    experience int NOT NULL,
    discount_percent int NOT NULL,
    icon nvarchar(512) NOT NULL CONSTRAINT df_member_level_icon DEFAULT N'',
    background_url nvarchar(512) NOT NULL CONSTRAINT df_member_level_background DEFAULT N'',
    status tinyint NOT NULL CONSTRAINT df_member_level_status DEFAULT 0,
    creator nvarchar(64) NULL CONSTRAINT df_member_level_creator DEFAULT N'',
    create_time datetime2 NOT NULL CONSTRAINT df_member_level_create_time DEFAULT CURRENT_TIMESTAMP,
    updater nvarchar(64) NULL CONSTRAINT df_member_level_updater DEFAULT N'',
    update_time datetime2 NOT NULL CONSTRAINT df_member_level_update_time DEFAULT CURRENT_TIMESTAMP,
    deleted bit NOT NULL CONSTRAINT df_member_level_deleted DEFAULT 0,
    tenant_id bigint NOT NULL CONSTRAINT df_member_level_tenant DEFAULT 1,
    CONSTRAINT ck_member_level_discount CHECK (discount_percent BETWEEN 0 AND 100)
);
CREATE UNIQUE INDEX uk_member_level_level ON dbo.member_level (tenant_id, level) WHERE deleted = 0;

CREATE TABLE dbo.member_group
(
    id bigint IDENTITY(1,1) NOT NULL CONSTRAINT pk_member_group PRIMARY KEY,
    name nvarchar(64) NOT NULL,
    remark nvarchar(255) NOT NULL CONSTRAINT df_member_group_remark DEFAULT N'',
    status tinyint NOT NULL CONSTRAINT df_member_group_status DEFAULT 0,
    creator nvarchar(64) NULL CONSTRAINT df_member_group_creator DEFAULT N'',
    create_time datetime2 NOT NULL CONSTRAINT df_member_group_create_time DEFAULT CURRENT_TIMESTAMP,
    updater nvarchar(64) NULL CONSTRAINT df_member_group_updater DEFAULT N'',
    update_time datetime2 NOT NULL CONSTRAINT df_member_group_update_time DEFAULT CURRENT_TIMESTAMP,
    deleted bit NOT NULL CONSTRAINT df_member_group_deleted DEFAULT 0,
    tenant_id bigint NOT NULL CONSTRAINT df_member_group_tenant DEFAULT 1
);
CREATE UNIQUE INDEX uk_member_group_name ON dbo.member_group (tenant_id, name) WHERE deleted = 0;

ALTER TABLE dbo.member_user ADD
    CONSTRAINT fk_member_user_level FOREIGN KEY (level_id) REFERENCES dbo.member_level(id),
    CONSTRAINT fk_member_user_group FOREIGN KEY (group_id) REFERENCES dbo.member_group(id);

CREATE TABLE dbo.member_experience_record
(
    id bigint IDENTITY(1,1) NOT NULL CONSTRAINT pk_member_experience_record PRIMARY KEY,
    user_id bigint NOT NULL,
    biz_type int NOT NULL,
    biz_id nvarchar(64) NOT NULL,
    title nvarchar(255) NOT NULL,
    description nvarchar(500) NULL,
    experience int NOT NULL,
    total_experience int NOT NULL,
    creator nvarchar(64) NULL CONSTRAINT df_member_experience_creator DEFAULT N'',
    create_time datetime2 NOT NULL CONSTRAINT df_member_experience_create_time DEFAULT CURRENT_TIMESTAMP,
    updater nvarchar(64) NULL CONSTRAINT df_member_experience_updater DEFAULT N'',
    update_time datetime2 NOT NULL CONSTRAINT df_member_experience_update_time DEFAULT CURRENT_TIMESTAMP,
    deleted bit NOT NULL CONSTRAINT df_member_experience_deleted DEFAULT 0,
    CONSTRAINT fk_member_experience_user FOREIGN KEY (user_id) REFERENCES dbo.member_user(id)
);
CREATE INDEX idx_member_experience_user ON dbo.member_experience_record (user_id, create_time DESC);

CREATE TABLE dbo.member_level_record
(
    id bigint IDENTITY(1,1) NOT NULL CONSTRAINT pk_member_level_record PRIMARY KEY,
    user_id bigint NOT NULL,
    level_id bigint NULL,
    level int NOT NULL,
    discount_percent int NOT NULL,
    experience int NOT NULL,
    user_experience int NOT NULL,
    remark nvarchar(255) NULL,
    description nvarchar(500) NULL,
    creator nvarchar(64) NULL CONSTRAINT df_member_level_record_creator DEFAULT N'',
    create_time datetime2 NOT NULL CONSTRAINT df_member_level_record_create_time DEFAULT CURRENT_TIMESTAMP,
    updater nvarchar(64) NULL CONSTRAINT df_member_level_record_updater DEFAULT N'',
    update_time datetime2 NOT NULL CONSTRAINT df_member_level_record_update_time DEFAULT CURRENT_TIMESTAMP,
    deleted bit NOT NULL CONSTRAINT df_member_level_record_deleted DEFAULT 0,
    CONSTRAINT fk_member_level_record_user FOREIGN KEY (user_id) REFERENCES dbo.member_user(id),
    CONSTRAINT fk_member_level_record_level FOREIGN KEY (level_id) REFERENCES dbo.member_level(id)
);
CREATE INDEX idx_member_level_record_user ON dbo.member_level_record (user_id, create_time DESC);

CREATE TABLE dbo.member_point_record
(
    id bigint IDENTITY(1,1) NOT NULL CONSTRAINT pk_member_point_record PRIMARY KEY,
    user_id bigint NOT NULL,
    biz_id nvarchar(64) NOT NULL,
    biz_type int NOT NULL,
    title nvarchar(255) NOT NULL,
    description nvarchar(500) NULL,
    point int NOT NULL,
    total_point int NOT NULL,
    creator nvarchar(64) NULL CONSTRAINT df_member_point_creator DEFAULT N'',
    create_time datetime2 NOT NULL CONSTRAINT df_member_point_create_time DEFAULT CURRENT_TIMESTAMP,
    updater nvarchar(64) NULL CONSTRAINT df_member_point_updater DEFAULT N'',
    update_time datetime2 NOT NULL CONSTRAINT df_member_point_update_time DEFAULT CURRENT_TIMESTAMP,
    deleted bit NOT NULL CONSTRAINT df_member_point_deleted DEFAULT 0,
    CONSTRAINT fk_member_point_user FOREIGN KEY (user_id) REFERENCES dbo.member_user(id)
);
CREATE INDEX idx_member_point_user ON dbo.member_point_record (user_id, create_time DESC);

CREATE TABLE dbo.member_config
(
    id bigint IDENTITY(1,1) NOT NULL CONSTRAINT pk_member_config PRIMARY KEY,
    point_trade_deduct_enable bit NOT NULL CONSTRAINT df_member_config_deduct DEFAULT 0,
    point_trade_deduct_unit_price int NOT NULL CONSTRAINT df_member_config_unit DEFAULT 0,
    point_trade_deduct_max_price int NOT NULL CONSTRAINT df_member_config_max DEFAULT 0,
    point_trade_give_point int NOT NULL CONSTRAINT df_member_config_give DEFAULT 0,
    creator nvarchar(64) NULL CONSTRAINT df_member_config_creator DEFAULT N'',
    create_time datetime2 NOT NULL CONSTRAINT df_member_config_create_time DEFAULT CURRENT_TIMESTAMP,
    updater nvarchar(64) NULL CONSTRAINT df_member_config_updater DEFAULT N'',
    update_time datetime2 NOT NULL CONSTRAINT df_member_config_update_time DEFAULT CURRENT_TIMESTAMP,
    deleted bit NOT NULL CONSTRAINT df_member_config_deleted DEFAULT 0
);

CREATE TABLE dbo.member_sign_in_config
(
    id bigint IDENTITY(1,1) NOT NULL CONSTRAINT pk_member_sign_in_config PRIMARY KEY,
    day int NOT NULL,
    point int NOT NULL,
    experience int NOT NULL,
    status tinyint NOT NULL CONSTRAINT df_member_sign_in_config_status DEFAULT 0,
    creator nvarchar(64) NULL CONSTRAINT df_member_sign_in_config_creator DEFAULT N'',
    create_time datetime2 NOT NULL CONSTRAINT df_member_sign_in_config_create_time DEFAULT CURRENT_TIMESTAMP,
    updater nvarchar(64) NULL CONSTRAINT df_member_sign_in_config_updater DEFAULT N'',
    update_time datetime2 NOT NULL CONSTRAINT df_member_sign_in_config_update_time DEFAULT CURRENT_TIMESTAMP,
    deleted bit NOT NULL CONSTRAINT df_member_sign_in_config_deleted DEFAULT 0
);
CREATE UNIQUE INDEX uk_member_sign_in_config_day ON dbo.member_sign_in_config (day) WHERE deleted = 0;

CREATE TABLE dbo.member_sign_in_record
(
    id bigint IDENTITY(1,1) NOT NULL CONSTRAINT pk_member_sign_in_record PRIMARY KEY,
    user_id bigint NOT NULL,
    day int NOT NULL,
    point int NOT NULL,
    experience int NOT NULL,
    creator nvarchar(64) NULL CONSTRAINT df_member_sign_in_record_creator DEFAULT N'',
    create_time datetime2 NOT NULL CONSTRAINT df_member_sign_in_record_create_time DEFAULT CURRENT_TIMESTAMP,
    updater nvarchar(64) NULL CONSTRAINT df_member_sign_in_record_updater DEFAULT N'',
    update_time datetime2 NOT NULL CONSTRAINT df_member_sign_in_record_update_time DEFAULT CURRENT_TIMESTAMP,
    deleted bit NOT NULL CONSTRAINT df_member_sign_in_record_deleted DEFAULT 0,
    CONSTRAINT fk_member_sign_in_record_user FOREIGN KEY (user_id) REFERENCES dbo.member_user(id)
);
CREATE INDEX idx_member_sign_in_record_user ON dbo.member_sign_in_record (user_id, create_time DESC);

CREATE TABLE dbo.yzj_store_profile
(
    id bigint IDENTITY(1,1) NOT NULL CONSTRAINT pk_yzj_store_profile PRIMARY KEY,
    dept_id bigint NOT NULL,
    store_code nvarchar(32) NOT NULL,
    store_level nvarchar(32) NULL,
    province nvarchar(32) NULL,
    city nvarchar(32) NULL,
    district nvarchar(32) NULL,
    address nvarchar(255) NULL,
    longitude decimal(10,7) NULL,
    latitude decimal(10,7) NULL,
    business_hours_json nvarchar(2000) NULL,
    version int NOT NULL CONSTRAINT df_yzj_store_version DEFAULT 0,
    creator nvarchar(64) NULL CONSTRAINT df_yzj_store_creator DEFAULT N'',
    create_time datetime2 NOT NULL CONSTRAINT df_yzj_store_create_time DEFAULT CURRENT_TIMESTAMP,
    updater nvarchar(64) NULL CONSTRAINT df_yzj_store_updater DEFAULT N'',
    update_time datetime2 NOT NULL CONSTRAINT df_yzj_store_update_time DEFAULT CURRENT_TIMESTAMP,
    deleted bit NOT NULL CONSTRAINT df_yzj_store_deleted DEFAULT 0,
    tenant_id bigint NOT NULL CONSTRAINT df_yzj_store_tenant DEFAULT 1,
    CONSTRAINT fk_yzj_store_dept FOREIGN KEY (dept_id) REFERENCES dbo.system_dept(id),
    CONSTRAINT ck_yzj_store_longitude CHECK (longitude IS NULL OR longitude BETWEEN -180 AND 180),
    CONSTRAINT ck_yzj_store_latitude CHECK (latitude IS NULL OR latitude BETWEEN -90 AND 90)
);
CREATE UNIQUE INDEX uk_yzj_store_dept ON dbo.yzj_store_profile (dept_id) WHERE deleted = 0;
CREATE UNIQUE INDEX uk_yzj_store_code ON dbo.yzj_store_profile (store_code) WHERE deleted = 0;

CREATE TABLE dbo.yzj_employee_profile
(
    id bigint IDENTITY(1,1) NOT NULL CONSTRAINT pk_yzj_employee_profile PRIMARY KEY,
    user_id bigint NOT NULL,
    employee_no nvarchar(32) NOT NULL,
    primary_store_dept_id bigint NOT NULL,
    hire_date date NULL,
    leave_date date NULL,
    can_service bit NOT NULL CONSTRAINT df_yzj_employee_can_service DEFAULT 1,
    can_sell bit NOT NULL CONSTRAINT df_yzj_employee_can_sell DEFAULT 1,
    employment_status varchar(16) NOT NULL CONSTRAINT df_yzj_employee_status DEFAULT 'ACTIVE',
    version int NOT NULL CONSTRAINT df_yzj_employee_version DEFAULT 0,
    creator nvarchar(64) NULL CONSTRAINT df_yzj_employee_creator DEFAULT N'',
    create_time datetime2 NOT NULL CONSTRAINT df_yzj_employee_create_time DEFAULT CURRENT_TIMESTAMP,
    updater nvarchar(64) NULL CONSTRAINT df_yzj_employee_updater DEFAULT N'',
    update_time datetime2 NOT NULL CONSTRAINT df_yzj_employee_update_time DEFAULT CURRENT_TIMESTAMP,
    deleted bit NOT NULL CONSTRAINT df_yzj_employee_deleted DEFAULT 0,
    tenant_id bigint NOT NULL CONSTRAINT df_yzj_employee_tenant DEFAULT 1,
    CONSTRAINT fk_yzj_employee_user FOREIGN KEY (user_id) REFERENCES dbo.system_users(id),
    CONSTRAINT fk_yzj_employee_store FOREIGN KEY (primary_store_dept_id) REFERENCES dbo.system_dept(id),
    CONSTRAINT ck_yzj_employee_status CHECK (employment_status IN ('ACTIVE', 'LEAVE')),
    CONSTRAINT ck_yzj_employee_dates CHECK (leave_date IS NULL OR hire_date IS NULL OR leave_date >= hire_date)
);
CREATE UNIQUE INDEX uk_yzj_employee_user ON dbo.yzj_employee_profile (user_id) WHERE deleted = 0;
CREATE UNIQUE INDEX uk_yzj_employee_no ON dbo.yzj_employee_profile (employee_no) WHERE deleted = 0;
CREATE INDEX idx_yzj_employee_store ON dbo.yzj_employee_profile (primary_store_dept_id, employment_status);

CREATE TABLE dbo.yzj_member_profile
(
    id bigint IDENTITY(1,1) NOT NULL CONSTRAINT pk_yzj_member_profile PRIMARY KEY,
    member_user_id bigint NOT NULL,
    member_no varchar(32) NOT NULL,
    full_name nvarchar(30) NOT NULL,
    mobile_hash char(64) NOT NULL,
    mobile_last4 char(4) NOT NULL,
    join_store_dept_id bigint NOT NULL,
    owner_store_dept_id bigint NOT NULL,
    advisor_user_id bigint NULL,
    source_type varchar(16) NOT NULL CONSTRAINT df_yzj_member_source DEFAULT 'MANUAL',
    special bit NOT NULL CONSTRAINT df_yzj_member_special DEFAULT 0,
    lifecycle_status varchar(16) NOT NULL CONSTRAINT df_yzj_member_lifecycle DEFAULT 'ACTIVE',
    frozen_at datetime2 NULL,
    freeze_reason nvarchar(255) NULL,
    version int NOT NULL CONSTRAINT df_yzj_member_version DEFAULT 0,
    creator nvarchar(64) NULL CONSTRAINT df_yzj_member_creator DEFAULT N'',
    create_time datetime2 NOT NULL CONSTRAINT df_yzj_member_create_time DEFAULT CURRENT_TIMESTAMP,
    updater nvarchar(64) NULL CONSTRAINT df_yzj_member_updater DEFAULT N'',
    update_time datetime2 NOT NULL CONSTRAINT df_yzj_member_update_time DEFAULT CURRENT_TIMESTAMP,
    deleted bit NOT NULL CONSTRAINT df_yzj_member_deleted DEFAULT 0,
    tenant_id bigint NOT NULL CONSTRAINT df_yzj_member_tenant DEFAULT 1,
    CONSTRAINT fk_yzj_member_user FOREIGN KEY (member_user_id) REFERENCES dbo.member_user(id),
    CONSTRAINT fk_yzj_member_join_store FOREIGN KEY (join_store_dept_id) REFERENCES dbo.system_dept(id),
    CONSTRAINT fk_yzj_member_owner_store FOREIGN KEY (owner_store_dept_id) REFERENCES dbo.system_dept(id),
    CONSTRAINT fk_yzj_member_advisor FOREIGN KEY (advisor_user_id) REFERENCES dbo.system_users(id),
    CONSTRAINT ck_yzj_member_source CHECK (source_type IN ('MANUAL', 'IMPORT', 'ONLINE', 'REFERRAL')),
    CONSTRAINT ck_yzj_member_lifecycle CHECK (lifecycle_status IN ('ACTIVE', 'FROZEN', 'LOST'))
);
CREATE UNIQUE INDEX uk_yzj_member_user ON dbo.yzj_member_profile (member_user_id) WHERE deleted = 0;
CREATE UNIQUE INDEX uk_yzj_member_no ON dbo.yzj_member_profile (member_no) WHERE deleted = 0;
CREATE UNIQUE INDEX uk_yzj_member_mobile ON dbo.yzj_member_profile (mobile_hash) WHERE deleted = 0;
CREATE INDEX idx_yzj_member_store ON dbo.yzj_member_profile (owner_store_dept_id, lifecycle_status);
CREATE INDEX idx_yzj_member_advisor ON dbo.yzj_member_profile (advisor_user_id);

CREATE TABLE dbo.yzj_membership_card
(
    id bigint IDENTITY(1,1) NOT NULL CONSTRAINT pk_yzj_membership_card PRIMARY KEY,
    member_profile_id bigint NOT NULL,
    card_no varchar(64) NOT NULL,
    register_store_dept_id bigint NOT NULL,
    status varchar(16) NOT NULL CONSTRAINT df_yzj_card_status DEFAULT 'ACTIVE',
    creator nvarchar(64) NULL CONSTRAINT df_yzj_card_creator DEFAULT N'',
    create_time datetime2 NOT NULL CONSTRAINT df_yzj_card_create_time DEFAULT CURRENT_TIMESTAMP,
    updater nvarchar(64) NULL CONSTRAINT df_yzj_card_updater DEFAULT N'',
    update_time datetime2 NOT NULL CONSTRAINT df_yzj_card_update_time DEFAULT CURRENT_TIMESTAMP,
    deleted bit NOT NULL CONSTRAINT df_yzj_card_deleted DEFAULT 0,
    CONSTRAINT fk_yzj_card_member FOREIGN KEY (member_profile_id) REFERENCES dbo.yzj_member_profile(id),
    CONSTRAINT fk_yzj_card_store FOREIGN KEY (register_store_dept_id) REFERENCES dbo.system_dept(id),
    CONSTRAINT ck_yzj_card_status CHECK (status IN ('ACTIVE', 'DISABLED', 'REPLACED'))
);
CREATE UNIQUE INDEX uk_yzj_card_no ON dbo.yzj_membership_card (card_no) WHERE deleted = 0;
CREATE UNIQUE INDEX uk_yzj_card_active_member ON dbo.yzj_membership_card (member_profile_id)
    WHERE deleted = 0 AND status = 'ACTIVE';

CREATE TABLE dbo.yzj_migration_batch
(
    id bigint IDENTITY(1,1) NOT NULL CONSTRAINT pk_yzj_migration_batch PRIMARY KEY,
    batch_code varchar(64) NOT NULL,
    source_system nvarchar(64) NOT NULL,
    source_snapshot nvarchar(255) NULL,
    status varchar(16) NOT NULL,
    started_at datetime2 NULL,
    finished_at datetime2 NULL,
    summary_json nvarchar(max) NULL,
    creator nvarchar(64) NULL CONSTRAINT df_yzj_migration_batch_creator DEFAULT N'',
    create_time datetime2 NOT NULL CONSTRAINT df_yzj_migration_batch_create_time DEFAULT CURRENT_TIMESTAMP,
    updater nvarchar(64) NULL CONSTRAINT df_yzj_migration_batch_updater DEFAULT N'',
    update_time datetime2 NOT NULL CONSTRAINT df_yzj_migration_batch_update_time DEFAULT CURRENT_TIMESTAMP,
    deleted bit NOT NULL CONSTRAINT df_yzj_migration_batch_deleted DEFAULT 0,
    CONSTRAINT ck_yzj_migration_batch_status CHECK (status IN ('CREATED', 'RUNNING', 'SUCCEEDED', 'FAILED', 'RECONCILING'))
);
CREATE UNIQUE INDEX uk_yzj_migration_batch_code ON dbo.yzj_migration_batch (batch_code) WHERE deleted = 0;

CREATE TABLE dbo.yzj_legacy_id_map
(
    id bigint IDENTITY(1,1) NOT NULL CONSTRAINT pk_yzj_legacy_id_map PRIMARY KEY,
    batch_id bigint NOT NULL,
    source_system nvarchar(64) NOT NULL,
    source_table nvarchar(128) NOT NULL,
    source_id nvarchar(128) NOT NULL,
    target_table nvarchar(128) NOT NULL,
    target_id bigint NOT NULL,
    source_checksum char(64) NULL,
    creator nvarchar(64) NULL CONSTRAINT df_yzj_legacy_map_creator DEFAULT N'',
    create_time datetime2 NOT NULL CONSTRAINT df_yzj_legacy_map_create_time DEFAULT CURRENT_TIMESTAMP,
    updater nvarchar(64) NULL CONSTRAINT df_yzj_legacy_map_updater DEFAULT N'',
    update_time datetime2 NOT NULL CONSTRAINT df_yzj_legacy_map_update_time DEFAULT CURRENT_TIMESTAMP,
    deleted bit NOT NULL CONSTRAINT df_yzj_legacy_map_deleted DEFAULT 0,
    CONSTRAINT fk_yzj_legacy_map_batch FOREIGN KEY (batch_id) REFERENCES dbo.yzj_migration_batch(id)
);
CREATE UNIQUE INDEX uk_yzj_legacy_source ON dbo.yzj_legacy_id_map
    (batch_id, source_table, source_id, target_table) WHERE deleted = 0;
CREATE INDEX idx_yzj_legacy_target ON dbo.yzj_legacy_id_map (target_table, target_id);

CREATE TABLE dbo.yzj_migration_reconciliation
(
    id bigint IDENTITY(1,1) NOT NULL CONSTRAINT pk_yzj_migration_reconciliation PRIMARY KEY,
    batch_id bigint NOT NULL,
    metric_code varchar(64) NOT NULL,
    source_count bigint NULL,
    target_count bigint NULL,
    source_amount decimal(20,4) NULL,
    target_amount decimal(20,4) NULL,
    matched bit NOT NULL CONSTRAINT df_yzj_reconciliation_matched DEFAULT 0,
    detail_json nvarchar(max) NULL,
    creator nvarchar(64) NULL CONSTRAINT df_yzj_reconciliation_creator DEFAULT N'',
    create_time datetime2 NOT NULL CONSTRAINT df_yzj_reconciliation_create_time DEFAULT CURRENT_TIMESTAMP,
    updater nvarchar(64) NULL CONSTRAINT df_yzj_reconciliation_updater DEFAULT N'',
    update_time datetime2 NOT NULL CONSTRAINT df_yzj_reconciliation_update_time DEFAULT CURRENT_TIMESTAMP,
    deleted bit NOT NULL CONSTRAINT df_yzj_reconciliation_deleted DEFAULT 0,
    CONSTRAINT fk_yzj_reconciliation_batch FOREIGN KEY (batch_id) REFERENCES dbo.yzj_migration_batch(id)
);
CREATE UNIQUE INDEX uk_yzj_reconciliation_metric ON dbo.yzj_migration_reconciliation
    (batch_id, metric_code) WHERE deleted = 0;

DECLARE @HeadOfficeDeptId bigint;
DECLARE @DemoStoreDeptId bigint;

SELECT @HeadOfficeDeptId = id FROM dbo.system_dept
WHERE name = N'悦指间总部' AND parent_id = 0 AND deleted = 0 AND tenant_id = 1;

IF @HeadOfficeDeptId IS NULL
BEGIN
    INSERT INTO dbo.system_dept
        (name, parent_id, sort, status, creator, create_time, updater, update_time, deleted, tenant_id)
    VALUES (N'悦指间总部', 0, 90, 0, N'system', CURRENT_TIMESTAMP, N'system', CURRENT_TIMESTAMP, 0, 1);
    SET @HeadOfficeDeptId = SCOPE_IDENTITY();
END;

SELECT @DemoStoreDeptId = id FROM dbo.system_dept
WHERE name = N'悦指间示范店' AND parent_id = @HeadOfficeDeptId AND deleted = 0 AND tenant_id = 1;

IF @DemoStoreDeptId IS NULL
BEGIN
    INSERT INTO dbo.system_dept
        (name, parent_id, sort, status, creator, create_time, updater, update_time, deleted, tenant_id)
    VALUES (N'悦指间示范店', @HeadOfficeDeptId, 1, 0, N'system', CURRENT_TIMESTAMP, N'system', CURRENT_TIMESTAMP, 0, 1);
    SET @DemoStoreDeptId = SCOPE_IDENTITY();
END;

INSERT INTO dbo.yzj_store_profile
    (dept_id, store_code, store_level, version, creator, create_time, updater, update_time, deleted, tenant_id)
VALUES (@DemoStoreDeptId, N'DEMO001', N'示范店', 0, N'system', CURRENT_TIMESTAMP, N'system', CURRENT_TIMESTAMP, 0, 1);

INSERT INTO dbo.member_level
    (name, level, experience, discount_percent, icon, background_url, status,
     creator, create_time, updater, update_time, deleted, tenant_id)
VALUES (N'普通会员', 1, 0, 100, N'', N'', 0,
        N'system', CURRENT_TIMESTAMP, N'system', CURRENT_TIMESTAMP, 0, 1);

INSERT INTO dbo.member_group
    (name, remark, status, creator, create_time, updater, update_time, deleted, tenant_id)
VALUES (N'默认分组', N'悦指间 P1 默认会员分组', 0,
        N'system', CURRENT_TIMESTAMP, N'system', CURRENT_TIMESTAMP, 0, 1);

INSERT INTO dbo.member_tag
    (name, creator, create_time, updater, update_time, deleted, tenant_id)
VALUES
    (N'新客', N'system', CURRENT_TIMESTAMP, N'system', CURRENT_TIMESTAMP, 0, 1),
    (N'高价值', N'system', CURRENT_TIMESTAMP, N'system', CURRENT_TIMESTAMP, 0, 1),
    (N'需回访', N'system', CURRENT_TIMESTAMP, N'system', CURRENT_TIMESTAMP, 0, 1);

INSERT INTO dbo.member_config
    (point_trade_deduct_enable, point_trade_deduct_unit_price, point_trade_deduct_max_price,
     point_trade_give_point, creator, create_time, updater, update_time, deleted)
VALUES (0, 0, 0, 0, N'system', CURRENT_TIMESTAMP, N'system', CURRENT_TIMESTAMP, 0);

DECLARE @MemberMenuId bigint = (SELECT TOP 1 id FROM dbo.system_menu WHERE path = N'user' AND component = N'member/user/index' AND deleted = 0);
DECLARE @DeptMenuId bigint = (SELECT TOP 1 parent_id FROM dbo.system_menu WHERE permission = N'system:dept:query' AND deleted = 0);
DECLARE @UserMenuId bigint = (SELECT TOP 1 parent_id FROM dbo.system_menu WHERE permission = N'system:user:query' AND deleted = 0);

IF @MemberMenuId IS NOT NULL AND NOT EXISTS (SELECT 1 FROM dbo.system_menu WHERE permission = N'yuezhijian:member:query' AND deleted = 0)
    INSERT INTO dbo.system_menu (name, permission, type, sort, parent_id, path, icon, component, component_name,
                                 status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
    VALUES (N'悦指间会员查询', N'yuezhijian:member:query', 3, 90, @MemberMenuId, N'', N'', N'', N'',
            0, '1', '1', '1', N'system', CURRENT_TIMESTAMP, N'system', CURRENT_TIMESTAMP, 0);

IF @MemberMenuId IS NOT NULL AND NOT EXISTS (SELECT 1 FROM dbo.system_menu WHERE permission = N'yuezhijian:member:create' AND deleted = 0)
    INSERT INTO dbo.system_menu (name, permission, type, sort, parent_id, path, icon, component, component_name,
                                 status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
    VALUES (N'悦指间会员建档', N'yuezhijian:member:create', 3, 91, @MemberMenuId, N'', N'', N'', N'',
            0, '1', '1', '1', N'system', CURRENT_TIMESTAMP, N'system', CURRENT_TIMESTAMP, 0);

IF @DeptMenuId IS NOT NULL AND NOT EXISTS (SELECT 1 FROM dbo.system_menu WHERE permission = N'yuezhijian:store:query' AND deleted = 0)
    INSERT INTO dbo.system_menu (name, permission, type, sort, parent_id, path, icon, component, component_name,
                                 status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
    VALUES (N'门店档案查询', N'yuezhijian:store:query', 3, 90, @DeptMenuId, N'', N'', N'', N'',
            0, '1', '1', '1', N'system', CURRENT_TIMESTAMP, N'system', CURRENT_TIMESTAMP, 0);

IF @DeptMenuId IS NOT NULL AND NOT EXISTS (SELECT 1 FROM dbo.system_menu WHERE permission = N'yuezhijian:store:update' AND deleted = 0)
    INSERT INTO dbo.system_menu (name, permission, type, sort, parent_id, path, icon, component, component_name,
                                 status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
    VALUES (N'门店档案维护', N'yuezhijian:store:update', 3, 91, @DeptMenuId, N'', N'', N'', N'',
            0, '1', '1', '1', N'system', CURRENT_TIMESTAMP, N'system', CURRENT_TIMESTAMP, 0);

IF @UserMenuId IS NOT NULL AND NOT EXISTS (SELECT 1 FROM dbo.system_menu WHERE permission = N'yuezhijian:employee:query' AND deleted = 0)
    INSERT INTO dbo.system_menu (name, permission, type, sort, parent_id, path, icon, component, component_name,
                                 status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
    VALUES (N'员工档案查询', N'yuezhijian:employee:query', 3, 90, @UserMenuId, N'', N'', N'', N'',
            0, '1', '1', '1', N'system', CURRENT_TIMESTAMP, N'system', CURRENT_TIMESTAMP, 0);

IF @UserMenuId IS NOT NULL AND NOT EXISTS (SELECT 1 FROM dbo.system_menu WHERE permission = N'yuezhijian:employee:update' AND deleted = 0)
    INSERT INTO dbo.system_menu (name, permission, type, sort, parent_id, path, icon, component, component_name,
                                 status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
    VALUES (N'员工档案维护', N'yuezhijian:employee:update', 3, 91, @UserMenuId, N'', N'', N'', N'',
            0, '1', '1', '1', N'system', CURRENT_TIMESTAMP, N'system', CURRENT_TIMESTAMP, 0);
