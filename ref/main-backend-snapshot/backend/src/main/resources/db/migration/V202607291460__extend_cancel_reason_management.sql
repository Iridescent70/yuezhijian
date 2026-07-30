-- 需求：系统管理-31、API-CFG-008/009、UI-CFG-004
-- 目的：复用既有取消原因表，补操作人、业务类型约束、账单原因、管理权限和页面菜单。
-- 恢复：共享环境执行后只通过更高版本Migration修复；不得删除已被历史业务引用的原因。

ALTER TABLE dbo.sys_cancel_reason ADD created_by bigint NULL, updated_by bigint NULL;
GO

ALTER TABLE dbo.sys_cancel_reason ADD
    CONSTRAINT fk_sys_cancel_reason_created_by FOREIGN KEY (created_by) REFERENCES dbo.iam_user(id),
    CONSTRAINT fk_sys_cancel_reason_updated_by FOREIGN KEY (updated_by) REFERENCES dbo.iam_user(id),
    CONSTRAINT ck_sys_cancel_reason_business_type
        CHECK (business_type IN ('APPOINTMENT', 'BILL', 'HOME_SERVICE'));

INSERT INTO dbo.sys_cancel_reason (
    business_type, reason_code, reason_name, requires_note, sort_no
)
VALUES
    ('BILL', 'CUSTOMER_CHANGE', N'客户取消消费', 0, 10),
    ('BILL', 'BILL_ERROR', N'开单错误', 1, 20),
    ('BILL', 'OTHER', N'其他', 1, 99);

INSERT INTO dbo.iam_permission (permission_code, permission_name, resource_type, api_pattern, http_method)
VALUES
    ('system:cancel-reason:view', N'查看取消原因', 'MENU', '/api/v1/cancel-reasons/**', 'GET'),
    ('system:cancel-reason:manage', N'维护取消原因', 'BUTTON', '/api/v1/cancel-reasons/**', 'POST,PUT');

INSERT INTO dbo.iam_role_permission (role_id, permission_id, effect)
SELECT role.id, permission.id, 'ALLOW'
FROM dbo.iam_role role CROSS JOIN dbo.iam_permission permission
WHERE role.role_code = 'HEADQUARTERS_ADMIN'
  AND permission.permission_code IN ('system:cancel-reason:view', 'system:cancel-reason:manage');

INSERT INTO dbo.iam_menu (
    parent_id, menu_code, name, route, icon, sort_no, client_type, permission_code
)
SELECT id, 'cancel-reasons', N'取消原因', '/app/system/cancel-reasons', 'CircleClose', 68,
       'PC', 'system:cancel-reason:view'
FROM dbo.iam_menu WHERE menu_code = 'system';

-- 验证：原预约原因保留，新增3项账单原因、2项权限和1个菜单；停用不删除历史引用。
