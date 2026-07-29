# 下一步开发入口

已完成迭代：`ITER-00`至`ITER-11`。次卡转赠已支持接收会员检索、有效期调整、原卡关闭、剩余资产新卡、双方流水和幂等执行。

## 持续开发顺序

1. 实现退卡试算、申请、审批和执行，按已消费项目原价重计，并复用ITER-09的版本/审批约束。
2. 建立退卡支付退款事实和原卡清零流水；为尚未开发的售卡/店长提成冲回保留明确接口和待处理状态，不能伪造已冲回结果。
3. 接入优惠券定义、会员券账户、结算可用券和原子核销。
4. 补齐结算后的回访任务、员工业绩归属和提成事实；冲销时追加负向提成。
5. 支付沙箱就绪后实现电子退款`PENDING/SUCCESS/FAILED`状态机、回调和对账；当前仅支持本地支付/退款事实联调。
6. SQL Server镜像就绪后验证14个Migration、rowversion、过滤唯一索引、会员/账户/卡种行锁、并发结算/冲销/换卡/转赠和事务回滚。

## 下次检查命令

```bash
cd /home/limeng/Documents/code/github/yuezhijian
git status --short
git log -3 --oneline
sed -n '1,280p' memory/DEVELOPMENT_STATUS.md
sed -n '1,200p' memory/NEXT_ACTIONS.md
```

若Memory与代码、测试或Git状态冲突，以仓库实际结果为准，并立即修正Memory。
