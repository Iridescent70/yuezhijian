# 下一步开发入口

已完成迭代：`ITER-00 本地开发基线`、`ITER-01 会员基础闭环`、`ITER-02 预约前置主数据`。

## 持续开发顺序

1. 开发预约纵向闭环：日历/排期查询、创建、详情、改期、到店、完成、取消和爽约。
2. 预约状态变更记录操作者、时间、原因和来源，并用版本号防止重复操作。
3. 建立预约创建与状态机自动化测试，前端实现排期页、新建预约和详情抽屉。
4. SQL Server镜像就绪后执行 `make infra-up`、`make db-init`、`make backend-dev-db`，验证5个Migration及数据库事务。
5. 预约闭环完成后进入账单草稿、账单行、混合支付试算和结算状态机。

## 下次检查命令

```bash
cd /home/limeng/Documents/code/github/yuezhijian
git status --short
git log -2 --oneline
sed -n '1,240p' memory/DEVELOPMENT_STATUS.md
sed -n '1,200p' memory/NEXT_ACTIONS.md
find backend/src frontend/src -type f | sort
```

若Memory与代码、测试或Git状态冲突，以仓库实际结果为准，并立即修正Memory。
