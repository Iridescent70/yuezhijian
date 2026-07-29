# 下一步开发入口

已完成迭代：`ITER-00 本地开发基线`、`ITER-01 会员基础闭环`。

## 持续开发顺序

1. SQL Server镜像就绪后执行 `make infra-up`、`make db-init`、`make backend-dev-db`，验证4个Migration、数据库登录及会员建档事务。
2. 建立服务项目、产品、员工、工位等预约/开单需要的最小主数据结构和查询API。
3. 开发预约纵向闭环：排期查询、创建、详情、改期、到店、取消和爽约。
4. 开发账单草稿、账单行、混合支付试算和结算状态机。
5. 取得甲方旧库备份后补齐真实旧表映射，并建立会员首轮迁移与对账脚本。

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
