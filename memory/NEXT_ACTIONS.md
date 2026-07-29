# 下一步开发入口

已完成迭代：`ITER-00`至`ITER-14`。版本化提成方案、服务账单计提、无规则待处理事实和整单冲销负向流水已经完成；卡业务提成、完整规则引擎及真实电子退款仍是明确的未完成项。

## 持续开发顺序

1. 接通次卡售卡正向提成、退卡原售卡提成冲回、换卡旧卡扣减与补差新提成，并更新退卡`commissionAdjustmentStatus`。
2. 增加次卡实耗、多人技师分配、阶梯/门店等级/岗位组合和跨月负向调整规则。
3. 补齐结算后的回访任务、服务分析和客诉跟进入口。
4. 按甲方确认结果扩展券的门店、项目、分类、时段、叠加和批量客群发放规则；当前基础闭环已可运行。
5. 支付沙箱就绪后实现电子退款`PENDING/SUCCESS/FAILED`状态机、回调和对账；当前仅支持本地支付/退款事实联调。
6. SQL Server镜像就绪后验证17个Migration、rowversion、过滤唯一索引、会员/账户/卡种/券码/提成并发及事务回滚。

## 下次检查命令

```bash
cd /home/limeng/Documents/code/github/yuezhijian
git status --short
git log -3 --oneline
sed -n '1,280p' memory/DEVELOPMENT_STATUS.md
sed -n '1,200p' memory/NEXT_ACTIONS.md
```

若Memory与代码、测试或Git状态冲突，以仓库实际结果为准，并立即修正Memory。
