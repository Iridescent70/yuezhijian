# 下一步开发入口

已完成迭代：`ITER-00`至`ITER-12`。次卡全生命周期已覆盖售卡、消费、换卡、转赠和退卡资产闭环；退卡提成冲回及真实电子退款仍是明确的未完成项。

## 持续开发顺序

1. 接入优惠券定义、会员券账户、结算可用券和原子核销，完成权益抵扣闭环。
2. 建立售卡/服务消耗提成事实、员工业绩归属和负向调整；接通换卡扣减、退卡技师/店长冲回及整单冲销负向提成。
3. 补齐结算后的回访任务、服务分析和客诉跟进入口。
4. 支付沙箱就绪后实现电子退款`PENDING/SUCCESS/FAILED`状态机、回调和对账；当前仅支持本地支付/退款事实联调。
5. SQL Server镜像就绪后验证15个Migration、rowversion、过滤唯一索引、会员/账户/卡种行锁、并发结算/冲销/换卡/转赠/退卡和事务回滚。

## 下次检查命令

```bash
cd /home/limeng/Documents/code/github/yuezhijian
git status --short
git log -3 --oneline
sed -n '1,280p' memory/DEVELOPMENT_STATUS.md
sed -n '1,200p' memory/NEXT_ACTIONS.md
```

若Memory与代码、测试或Git状态冲突，以仓库实际结果为准，并立即修正Memory。
