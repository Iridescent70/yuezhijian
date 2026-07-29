# V202607291280 会员资料状态与标签

## 对应脚本

`backend/src/main/resources/db/migration/V202607291280__extend_member_management.sql`

## 变更内容

- 新建`mem_member_status_log`，保存会员冻结、解冻和停用的前后状态、原因、人员和时间。
- 为会员状态历史建立`member_id + changed_at + id`倒序索引。
- 增加会员维护和标签查看/维护权限，并授权总部管理员及店长。

## 约束

- 前后状态必须属于`ACTIVE/FROZEN/INACTIVE`且不能相同。
- 变更原因不能为空；历史只追加，不通过更新覆盖。
- 资料与标签操作继续依赖`mem_member.row_version`进行并发控制。

## 执行记录

| 环境 | 状态 | 说明 |
| --- | --- | --- |
| memory开发档 | 已验证 | 端到端流程和并发版本已覆盖 |
| SQL Server本地空库 | 待执行 | 当前缺镜像及Docker socket权限 |
| 测试/生产 | 未执行 | 按Flyway顺序执行，禁止手工改已发布脚本 |

## 回滚

共享环境不执行自动降级。上线前备份；若上线验证失败，回退应用并按数据审计决定是否保留状态历史表，不能直接删除已产生的会员操作记录。
