# 下一步开发入口

已完成迭代：`ITER-00`至`ITER-10`。次卡换卡已支持剩余价值试算、目标卡种版本快照、精确补差、原卡关闭、新卡建档、双向流水和幂等执行。

## 持续开发顺序

1. 实现次卡转赠：接收会员校验、原卡关闭、目标会员新卡、有效期规则、双向流水和幂等执行。
2. 实现退卡试算、申请、审批和执行，复用ITER-09的版本/审批约束，并补支付退款与售卡提成冲回接口边界。
3. 接入优惠券定义、会员券账户、结算可用券和原子核销。
4. 补齐结算后的回访任务、员工业绩归属和提成事实；冲销时追加负向提成。
5. 支付沙箱就绪后实现电子退款`PENDING/SUCCESS/FAILED`状态机、回调和对账；当前仅支持本地支付/退款事实联调。
6. SQL Server镜像就绪后验证13个Migration、rowversion、过滤唯一索引、账户/卡种行锁、并发结算/冲销/换卡和事务回滚。

## 下次检查命令

```bash
cd /home/limeng/Documents/code/github/yuezhijian
git status --short
git log -3 --oneline
sed -n '1,280p' memory/DEVELOPMENT_STATUS.md
sed -n '1,200p' memory/NEXT_ACTIONS.md
```

若Memory与代码、测试或Git状态冲突，以仓库实际结果为准，并立即修正Memory。
