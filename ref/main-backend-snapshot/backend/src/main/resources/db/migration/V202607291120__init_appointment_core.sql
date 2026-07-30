-- 需求：API-APT-001、003~009，业务管理-03、预约管理-01~05
-- 目的：建立预约主档、项目快照、技师占用、取消原因和不可变状态历史。

CREATE TABLE dbo.sys_cancel_reason (
    id bigint IDENTITY(1,1) NOT NULL CONSTRAINT pk_sys_cancel_reason PRIMARY KEY,
    business_type varchar(32) NOT NULL,
    reason_code varchar(64) NOT NULL,
    reason_name nvarchar(200) NOT NULL,
    requires_note bit NOT NULL CONSTRAINT df_sys_cancel_reason_requires_note DEFAULT (0),
    sort_no int NOT NULL CONSTRAINT df_sys_cancel_reason_sort DEFAULT (0),
    status varchar(32) NOT NULL CONSTRAINT df_sys_cancel_reason_status DEFAULT ('ACTIVE'),
    created_at datetime2(3) NOT NULL CONSTRAINT df_sys_cancel_reason_created_at DEFAULT (sysdatetime()),
    updated_at datetime2(3) NOT NULL CONSTRAINT df_sys_cancel_reason_updated_at DEFAULT (sysdatetime()),
    row_version rowversion NOT NULL,
    CONSTRAINT uq_sys_cancel_reason UNIQUE (business_type, reason_code),
    CONSTRAINT ck_sys_cancel_reason_status CHECK (status IN ('ACTIVE', 'DISABLED'))
);

CREATE TABLE dbo.apt_appointment (
    id bigint IDENTITY(1,1) NOT NULL CONSTRAINT pk_apt_appointment PRIMARY KEY,
    appointment_no varchar(32) NOT NULL,
    member_id bigint NULL,
    guest_name nvarchar(100) NULL,
    mobile_ciphertext nvarchar(500) NULL,
    mobile_hash char(64) NULL,
    mobile_last4 char(4) NULL,
    store_id bigint NOT NULL,
    source_type varchar(32) NOT NULL,
    appointment_type varchar(32) NOT NULL CONSTRAINT df_apt_appointment_type DEFAULT ('IN_STORE'),
    start_at datetime2(3) NOT NULL,
    end_at datetime2(3) NOT NULL,
    person_count int NOT NULL CONSTRAINT df_apt_appointment_person_count DEFAULT (1),
    workstation_id bigint NULL,
    note nvarchar(1000) NULL,
    status varchar(32) NOT NULL CONSTRAINT df_apt_appointment_status DEFAULT ('PENDING_CONFIRM'),
    arrived_at datetime2(3) NULL,
    started_at datetime2(3) NULL,
    completed_at datetime2(3) NULL,
    cancelled_at datetime2(3) NULL,
    cancel_reason_id bigint NULL,
    cancel_note nvarchar(500) NULL,
    bill_id bigint NULL,
    idempotency_key varchar(128) NULL,
    created_at datetime2(3) NOT NULL CONSTRAINT df_apt_appointment_created_at DEFAULT (sysdatetime()),
    created_by bigint NOT NULL,
    updated_at datetime2(3) NOT NULL CONSTRAINT df_apt_appointment_updated_at DEFAULT (sysdatetime()),
    updated_by bigint NOT NULL,
    row_version rowversion NOT NULL,
    CONSTRAINT uq_apt_appointment_no UNIQUE (appointment_no),
    CONSTRAINT fk_apt_appointment_member FOREIGN KEY (member_id) REFERENCES dbo.mem_member(id),
    CONSTRAINT fk_apt_appointment_store FOREIGN KEY (store_id) REFERENCES dbo.org_store(id),
    CONSTRAINT fk_apt_appointment_workstation FOREIGN KEY (workstation_id) REFERENCES dbo.org_workstation(id),
    CONSTRAINT fk_apt_appointment_cancel_reason FOREIGN KEY (cancel_reason_id) REFERENCES dbo.sys_cancel_reason(id),
    CONSTRAINT ck_apt_appointment_period CHECK (end_at > start_at),
    CONSTRAINT ck_apt_appointment_customer CHECK (member_id IS NOT NULL OR (guest_name IS NOT NULL AND mobile_last4 IS NOT NULL)),
    CONSTRAINT ck_apt_appointment_person_count CHECK (person_count BETWEEN 1 AND 100),
    CONSTRAINT ck_apt_appointment_source CHECK (source_type IN ('PC', 'MOBILE', 'CUSTOMER', 'IMPORT')),
    CONSTRAINT ck_apt_appointment_type CHECK (appointment_type IN ('IN_STORE', 'HOME_SERVICE')),
    CONSTRAINT ck_apt_appointment_status CHECK (status IN (
        'PENDING_CONFIRM', 'CONFIRMED', 'ARRIVED', 'SERVING', 'COMPLETED', 'CANCELLED', 'NO_SHOW'
    ))
);

CREATE UNIQUE INDEX ux_apt_appointment_idempotency
    ON dbo.apt_appointment (idempotency_key) WHERE idempotency_key IS NOT NULL;
CREATE INDEX ix_apt_appointment_store_start
    ON dbo.apt_appointment (store_id, start_at, status) INCLUDE (end_at, member_id, workstation_id);
CREATE INDEX ix_apt_appointment_member_start
    ON dbo.apt_appointment (member_id, start_at DESC) WHERE member_id IS NOT NULL;

CREATE TABLE dbo.apt_appointment_service (
    id bigint IDENTITY(1,1) NOT NULL CONSTRAINT pk_apt_appointment_service PRIMARY KEY,
    appointment_id bigint NOT NULL,
    service_id bigint NOT NULL,
    service_name_snapshot nvarchar(200) NOT NULL,
    duration_minutes int NOT NULL,
    price_snapshot decimal(19,4) NOT NULL,
    sort_no int NOT NULL CONSTRAINT df_apt_appointment_service_sort DEFAULT (0),
    CONSTRAINT fk_apt_appointment_service_appointment FOREIGN KEY (appointment_id) REFERENCES dbo.apt_appointment(id),
    CONSTRAINT fk_apt_appointment_service_service FOREIGN KEY (service_id) REFERENCES dbo.cat_service(id),
    CONSTRAINT ck_apt_appointment_service_duration CHECK (duration_minutes BETWEEN 5 AND 1440),
    CONSTRAINT ck_apt_appointment_service_price CHECK (price_snapshot >= 0),
    CONSTRAINT uq_apt_appointment_service UNIQUE (appointment_id, service_id)
);

CREATE INDEX ix_apt_appointment_service_service
    ON dbo.apt_appointment_service (service_id, appointment_id);

CREATE TABLE dbo.apt_appointment_employee (
    id bigint IDENTITY(1,1) NOT NULL CONSTRAINT pk_apt_appointment_employee PRIMARY KEY,
    appointment_id bigint NOT NULL,
    employee_id bigint NOT NULL,
    role_type varchar(32) NOT NULL CONSTRAINT df_apt_appointment_employee_role DEFAULT ('PRIMARY'),
    start_at datetime2(3) NOT NULL,
    end_at datetime2(3) NOT NULL,
    is_designated bit NOT NULL CONSTRAINT df_apt_appointment_employee_designated DEFAULT (0),
    CONSTRAINT fk_apt_appointment_employee_appointment FOREIGN KEY (appointment_id) REFERENCES dbo.apt_appointment(id),
    CONSTRAINT fk_apt_appointment_employee_employee FOREIGN KEY (employee_id) REFERENCES dbo.org_employee(id),
    CONSTRAINT ck_apt_appointment_employee_role CHECK (role_type IN ('PRIMARY', 'ASSISTANT')),
    CONSTRAINT ck_apt_appointment_employee_period CHECK (end_at > start_at),
    CONSTRAINT uq_apt_appointment_employee UNIQUE (appointment_id, employee_id, role_type)
);

CREATE INDEX ix_apt_appointment_employee_occupancy
    ON dbo.apt_appointment_employee (employee_id, start_at, end_at, appointment_id);

CREATE TABLE dbo.apt_status_history (
    id bigint IDENTITY(1,1) NOT NULL CONSTRAINT pk_apt_status_history PRIMARY KEY,
    appointment_id bigint NOT NULL,
    from_status varchar(32) NULL,
    to_status varchar(32) NOT NULL,
    reason_code varchar(64) NULL,
    note nvarchar(500) NULL,
    occurred_at datetime2(3) NOT NULL CONSTRAINT df_apt_status_history_occurred_at DEFAULT (sysdatetime()),
    operator_id bigint NOT NULL,
    CONSTRAINT fk_apt_status_history_appointment FOREIGN KEY (appointment_id) REFERENCES dbo.apt_appointment(id),
    CONSTRAINT fk_apt_status_history_operator FOREIGN KEY (operator_id) REFERENCES dbo.iam_user(id)
);

CREATE INDEX ix_apt_status_history_appointment
    ON dbo.apt_status_history (appointment_id, occurred_at, id);

INSERT INTO dbo.sys_cancel_reason (business_type, reason_code, reason_name, requires_note, sort_no)
VALUES
    ('APPOINTMENT', 'CUSTOMER_CHANGE', N'客户行程有变', 0, 10),
    ('APPOINTMENT', 'STORE_CAPACITY', N'门店接待能力不足', 1, 20),
    ('APPOINTMENT', 'CUSTOMER_NO_SHOW', N'客户未按时到店', 0, 30),
    ('APPOINTMENT', 'OTHER', N'其他', 1, 99);

INSERT INTO dbo.iam_permission (permission_code, permission_name, resource_type, api_pattern, http_method)
VALUES ('appointment:appointment:manage', N'处理预约', 'BUTTON', '/api/v1/appointments/**', 'POST');

INSERT INTO dbo.iam_role_permission (role_id, permission_id, effect)
SELECT r.id, p.id, 'ALLOW'
FROM dbo.iam_role r
CROSS JOIN dbo.iam_permission p
WHERE r.role_code = 'HEADQUARTERS_ADMIN'
  AND p.permission_code = 'appointment:appointment:manage';

-- 验证：新增5张表、4项取消原因及1项预约处理权限。
