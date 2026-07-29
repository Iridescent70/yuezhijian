# 下一步开发入口

已完成迭代：`ITER-00`至`ITER-17`。服务、卡业务、转赠谱系、账单冲销提成及无写入模拟测算已完成；完整规则引擎和真实电子退款仍是明确的未完成项。

## 持续开发顺序

1. 补齐结算后的回访任务、服务分析和客诉跟进入口。
2. 甲方确认六种模式名称、阶梯边界、跨档算法和计算周期后，接入累计阶梯并扩展多人技师/售卡分配。
3. 补充门店等级、岗位组合、店长提成和跨月负向调整规则及工资单。
4. 按甲方确认结果扩展券的门店、项目、分类、时段、叠加和批量客群发放规则；当前基础闭环已可运行。
5. 支付沙箱就绪后实现电子退款`PENDING/SUCCESS/FAILED`状态机、回调和对账；当前仅支持本地支付/退款事实联调。
6. SQL Server镜像就绪后验证18个Migration、rowversion、过滤唯一索引、会员/账户/卡种/券码/提成并发及事务回滚。

## 下次检查命令

```bash
cd /home/limeng/Documents/code/github/yuezhijian
git status --short
git log -3 --oneline
sed -n '1,280p' memory/DEVELOPMENT_STATUS.md
sed -n '1,200p' memory/NEXT_ACTIONS.md
```

若Memory与代码、测试或Git状态冲突，以仓库实际结果为准，并立即修正Memory。
