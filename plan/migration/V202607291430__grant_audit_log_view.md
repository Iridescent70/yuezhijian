# V202607291430 操作日志查询权限

| 项目 | 内容 |
| --- | --- |
| SQL文件 | `V202607291430__grant_audit_log_view.sql` |
| 日期 | 2026-07-30 |
| 需求 | 系统管理-07、API-COM-006/007、UI-IAM-005 |
| 影响表 | `iam_permission`、`iam_role_permission`、`iam_menu` |
| 风险 | 低 |

## 变更内容

- 新增`system:audit:view`权限并默认授予总部管理员。
- 在系统管理下新增`/app/system/audit-logs`操作日志菜单。
- `sys_audit_log`及其日期、对象、用户索引已由V0900建立，本次不修改日志表。

## 验证

```sql
SELECT permission_code FROM dbo.iam_permission WHERE permission_code = 'system:audit:view';
SELECT menu_code, route FROM dbo.iam_menu WHERE menu_code = 'audit-logs';
```

预期分别返回1项权限和1项菜单。

## 恢复

上线前可按菜单、角色权限、权限的顺序删除。共享环境执行后不直接修改本脚本，发现问题增加后续Migration。

## 执行记录

| 环境 | 执行时间 | 执行人/流水线 | 结果 | 证据 |
| --- | --- | --- | --- | --- |
| 本地memory | 2026-07-30 | Codex | 不适用 | memory测试不执行Flyway |
| 本地SQL Server | 待执行 | 待环境就绪 | BLOCKED | 当前Docker socket不可用 |
