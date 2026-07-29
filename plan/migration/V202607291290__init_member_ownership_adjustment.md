# V202607291290 会员归属调整

## 对应脚本

`backend/src/main/resources/db/migration/V202607291290__init_member_ownership_adjustment.sql`

## 变更内容

- 新建`mem_ownership_adjustment`，保存申请、审批和执行全生命周期。
- 通过`active_member_key`持久化计算列和唯一索引限制单会员活动申请；活动记录使用会员id，结束记录使用负申请id。
- 增加审批/执行队列索引及会员历史索引。
- 增加归属调整查看、申请、审批权限，并把会员菜单调整为“会员列表/归属调整”子菜单。

## 执行规则

- `PENDING/WAITING`表示待审批；通过后为`APPROVED/WAITING`，到期领取后短暂进入`PROCESSING`。
- 会员当前门店成功切换后为`APPLIED`；旧门店与快照不一致时为`FAILED`；驳回为`REJECTED/CANCELLED`。
- 归属执行只更新`mem_member.owner_store_id`并清空旧顾问，不更新历史交易表。
- 分润JSON只保存快照，后续分润模块按已确认公式读取或关联，不能据此宣称已结算。

## 执行记录

| 环境 | 状态 | 说明 |
| --- | --- | --- |
| memory开发档 | 已验证 | 同日、未来、驳回、排他和版本冲突已覆盖 |
| SQL Server本地空库 | 待执行 | 当前缺镜像及Docker socket权限 |
| 测试/生产 | 未执行 | 上线前验证定时执行、唯一索引和事务回滚 |

## 回滚

共享环境不执行Flyway降级。应用回退前先暂停归属到期任务并导出`WAITING/PROCESSING`申请；已生效的会员归属不得通过删除申请记录回滚，必须创建反向调整并重新审批。
