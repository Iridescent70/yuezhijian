-- 需求：薪酬-01、API-COMM-003、UI-COMM-002
-- 目的：增加无写入的薪资测算菜单，复用提成方案查看权限。

UPDATE dbo.iam_menu
SET sort_no = 30
WHERE menu_code = 'commission-ledgers';

INSERT INTO dbo.iam_menu (parent_id, menu_code, name, route, icon, sort_no, client_type, permission_code)
SELECT id, 'commission-simulator', N'薪资测算', '/app/commission/simulator', 'DataAnalysis', 20,
       'PC', 'commission:plan:view'
FROM dbo.iam_menu
WHERE menu_code = 'commission';

-- 验证：总部管理员可见“提成方案、薪资测算、提成流水”，测算不写任何业务表。
