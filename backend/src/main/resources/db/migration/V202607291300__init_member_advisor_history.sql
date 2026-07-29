-- 需求：API-MEM-020、优化会员管理-01
-- 目的：批量分配顾问保留变更前后值，不能只覆盖会员主档。

CREATE TABLE dbo.mem_member_advisor_log (
    id bigint IDENTITY(1,1) NOT NULL CONSTRAINT pk_mem_member_advisor_log PRIMARY KEY,
    member_id bigint NOT NULL,
    owner_store_id bigint NOT NULL,
    old_advisor_employee_id bigint NULL,
    new_advisor_employee_id bigint NULL,
    change_source varchar(32) NOT NULL,
    changed_at datetime2(3) NOT NULL CONSTRAINT df_mem_advisor_log_changed DEFAULT (sysdatetime()),
    changed_by bigint NOT NULL,
    CONSTRAINT fk_mem_advisor_log_member FOREIGN KEY (member_id) REFERENCES dbo.mem_member(id),
    CONSTRAINT fk_mem_advisor_log_store FOREIGN KEY (owner_store_id) REFERENCES dbo.org_store(id),
    CONSTRAINT fk_mem_advisor_log_old_employee FOREIGN KEY (old_advisor_employee_id) REFERENCES dbo.org_employee(id),
    CONSTRAINT fk_mem_advisor_log_new_employee FOREIGN KEY (new_advisor_employee_id) REFERENCES dbo.org_employee(id),
    CONSTRAINT fk_mem_advisor_log_user FOREIGN KEY (changed_by) REFERENCES dbo.iam_user(id),
    CONSTRAINT ck_mem_advisor_log_source CHECK (change_source IN ('BATCH_ASSIGN', 'MANUAL', 'IMPORT')),
    CONSTRAINT ck_mem_advisor_log_changed CHECK (
        (old_advisor_employee_id IS NULL AND new_advisor_employee_id IS NOT NULL)
        OR (old_advisor_employee_id IS NOT NULL AND new_advisor_employee_id IS NULL)
        OR old_advisor_employee_id <> new_advisor_employee_id
    )
);

CREATE INDEX ix_mem_advisor_log_member
    ON dbo.mem_member_advisor_log (member_id, changed_at DESC, id DESC);

CREATE INDEX ix_mem_advisor_log_employee
    ON dbo.mem_member_advisor_log (new_advisor_employee_id, changed_at DESC, id DESC);
