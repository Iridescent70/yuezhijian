# V202607291410 职务维护权限和约束

| 项目 | 内容 |
| --- | --- |
| SQL文件 | `V202607291410__grant_position_management.sql` |
| 日期 | 2026-07-30 |
| 需求 | 系统管理-10、API-ORG-006/007、UI-ORG-003 |
| 影响表 | `org_position`、`iam_permission`、`iam_role_permission`、`iam_menu` |
| 风险 | 低 |

## 变更内容

- 为`org_position.status`增加`ACTIVE/DISABLED`检查约束，字段和默认值仍沿用V0910。
- 新增`org:position:view/manage`权限，并默认授予总部管理员。
- 在系统管理下新增`/app/system/positions`职务管理菜单。

## 验证

```sql
SELECT name FROM sys.check_constraints WHERE name = 'ck_org_position_status';
SELECT permission_code FROM dbo.iam_permission WHERE permission_code LIKE 'org:position:%';
SELECT menu_code, route FROM dbo.iam_menu WHERE menu_code = 'positions';
```

预期分别返回1项约束、2项权限和1项菜单；员工和提成选择接口默认仍只返回启用职务。

## 恢复

上线前可删除菜单、角色权限、权限和检查约束。进入共享环境后不修改本脚本；发现问题增加后续Migration修复。

## 执行记录

| 环境 | 执行时间 | 执行人/流水线 | 结果 | 证据 |
| --- | --- | --- | --- | --- |
| 本地memory | 2026-07-30 | Codex | 不适用 | memory测试不执行Flyway |
| 本地SQL Server | 待执行 | 待环境就绪 | BLOCKED | 当前Docker socket不可用 |
