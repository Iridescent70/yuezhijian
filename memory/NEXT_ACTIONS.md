# 下一步开发入口

已完成迭代：`ITER-00`至`ITER-06`。会员储值、积分和次卡均已有当前余额及不可变流水；账单目前仍只支持外部支付方式。

## 持续开发顺序

1. 扩展结算试算，按账单项目返回可用次卡匹配、储值余额和积分抵扣上限。
2. 结算请求增加储值、积分和次卡选择；同一事务锁定并扣减资产、写流水、完成账单。
3. 增加资产版本快照，试算后资产变化时拒绝结算，覆盖并发余额/扣次测试。
4. 完成账单行编辑删除、优惠分摊和资产支付后的支付展示。
5. 继续实现换卡、转赠、退卡审批和反向流水。
6. SQL Server镜像就绪后验证9个Migration、rowversion、账户行锁和并发售卡。

## 下次检查命令

```bash
cd /home/limeng/Documents/code/github/yuezhijian
git status --short
git log -2 --oneline
sed -n '1,280p' memory/DEVELOPMENT_STATUS.md
sed -n '1,200p' memory/NEXT_ACTIONS.md
```

若Memory与代码、测试或Git状态冲突，以仓库实际结果为准，并立即修正Memory。
