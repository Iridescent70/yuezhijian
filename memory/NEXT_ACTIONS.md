# 下一步开发入口

已完成迭代：`ITER-00`至`ITER-09`。账单已支持项目维护、优惠分摊、组合结算和整单冲销；冲销执行会追加支付退款事实及储值、积分、次卡反向流水。

## 持续开发顺序

1. 实现换卡试算、补差支付、原卡关闭、新卡建档和双向资产流水。
2. 实现次卡转赠和退卡申请/审批，复用ITER-09的版本与幂等执行约束。
3. 接入优惠券定义、会员券账户、结算可用券和原子核销。
4. 补齐结算后的回访任务、员工业绩归属和提成事实；冲销时追加负向提成。
5. 支付沙箱就绪后实现电子退款`PENDING/SUCCESS/FAILED`状态机、回调和对账；当前仅支持本地退款事实联调。
6. SQL Server镜像就绪后验证12个Migration、rowversion、过滤唯一索引、账户行锁、并发结算/冲销和事务回滚。

## 下次检查命令

```bash
cd /home/limeng/Documents/code/github/yuezhijian
git status --short
git log -3 --oneline
sed -n '1,280p' memory/DEVELOPMENT_STATUS.md
sed -n '1,200p' memory/NEXT_ACTIONS.md
```

若Memory与代码、测试或Git状态冲突，以仓库实际结果为准，并立即修正Memory。
