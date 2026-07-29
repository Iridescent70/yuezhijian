# 下一步开发入口

已完成迭代：`ITER-00`至`ITER-08`。账单已支持项目维护、优惠分摊、储值/积分/次卡与外部支付组合结算，并有版本控制和完整明细展示。

## 持续开发顺序

1. 建立退款/冲销主单、试算、审批状态和幂等执行框架。
2. 为支付、储值、积分和次卡生成可追溯的反向流水，禁止直接改历史流水。
3. 实现换卡、转赠和退卡试算、审批及原卡/新卡资产追踪。
4. 接入优惠券定义、会员券账户、结算可用券和原子核销。
5. 补齐结算后的回访任务、员工业绩归属和提成事实。
6. SQL Server镜像就绪后验证11个Migration、rowversion、账户行锁、并发结算和事务回滚。

## 下次检查命令

```bash
cd /home/limeng/Documents/code/github/yuezhijian
git status --short
git log -3 --oneline
sed -n '1,280p' memory/DEVELOPMENT_STATUS.md
sed -n '1,200p' memory/NEXT_ACTIONS.md
```

若Memory与代码、测试或Git状态冲突，以仓库实际结果为准，并立即修正Memory。
