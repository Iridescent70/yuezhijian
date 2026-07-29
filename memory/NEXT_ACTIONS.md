# 下一步开发入口

已完成迭代：`ITER-00 本地开发基线`、`ITER-01 会员基础闭环`、`ITER-02 预约前置主数据`、`ITER-03 预约核心闭环`。

## 持续开发顺序

1. 建立账单主档、账单行、服务人员分配、支付方式、支付单和支付明细Migration。
2. 开发手工开单及预约到店转账单，项目、会员、技师和门店信息从预约复制后允许按权限调整。
3. 开发账单试算、优惠分摊、混合支付和结算状态机，金额统一使用BigDecimal/decimal(19,4)。
4. 实现账单列表、新建账单、结算台和详情页面，并建立金额黄金样例测试。
5. SQL Server镜像就绪后验证6个既有Migration、数据库预约事务及并发冲突用例。

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
