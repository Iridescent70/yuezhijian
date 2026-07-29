# V202607291420 分类与单位维护权限

| 项目 | 内容 |
| --- | --- |
| SQL文件 | `V202607291420__grant_catalog_master_management.sql` |
| 日期 | 2026-07-30 |
| 需求 | 系统管理-11/12/21、API-CAT-001/002/003、UI-CAT-002 |
| 影响表 | `iam_permission`、`iam_role_permission`、`iam_menu` |
| 风险 | 低 |

## 变更内容

- 新增`catalog:master:view/manage`权限，并默认授予总部管理员。
- 在基础资料下新增`/app/catalog/units`“分类与单位”菜单。
- `cat_category`和`cat_unit`的字段、唯一约束、状态约束及`rowversion`已由V0920/V1110建立，本次不重复修改表结构。

当前合同只明确产品和服务分类，没有确认多级分类的移动、级联停用和统计口径。首版页面因此仅维护一级分类；数据库保留`parent_id/path`，待规则书面确认后通过后续Migration扩展。

## 验证

```sql
SELECT permission_code FROM dbo.iam_permission
WHERE permission_code IN ('catalog:master:view', 'catalog:master:manage');
SELECT menu_code, route FROM dbo.iam_menu WHERE menu_code = 'catalog-master-data';
SELECT COUNT(*) AS category_count FROM dbo.cat_category;
SELECT COUNT(*) AS unit_count FROM dbo.cat_unit;
```

预期返回2项权限、1项菜单，且分类及单位数量与执行前一致。

## 恢复

上线前可按菜单、角色权限、权限的顺序删除。本脚本不改业务数据；共享环境执行后不直接修改本脚本，发现问题增加后续Migration。

## 执行记录

| 环境 | 执行时间 | 执行人/流水线 | 结果 | 证据 |
| --- | --- | --- | --- | --- |
| 本地memory | 2026-07-30 | Codex | 不适用 | memory测试不执行Flyway |
| 本地SQL Server | 待执行 | 待环境就绪 | BLOCKED | 当前Docker socket不可用 |
