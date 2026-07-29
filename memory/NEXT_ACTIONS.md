# 下一步开发入口

已完成迭代：`ITER-00`至`ITER-07`。账单已支持储值、积分、次卡与外部支付组合结算，并有资产版本快照、不可变流水和结算幂等。

## 持续开发顺序

1. 完成账单行编辑/删除、优惠分摊和账单资产使用明细展示。
2. 建立退款/冲销主单、审批状态和支付/储值/积分/次卡反向流水。
3. 实现换卡、转赠和退卡试算、审批及原卡/新卡资产追踪。
4. 补齐结算后的回访任务、员工业绩归属和提成事实。
5. SQL Server镜像就绪后验证10个Migration、rowversion、账户行锁、并发结算和事务回滚。

## 下次检查命令

```bash
cd /home/limeng/Documents/code/github/yuezhijian
git status --short
git log -3 --oneline
sed -n '1,280p' memory/DEVELOPMENT_STATUS.md
sed -n '1,200p' memory/NEXT_ACTIONS.md
```

若Memory与代码、测试或Git状态冲突，以仓库实际结果为准，并立即修正Memory。
