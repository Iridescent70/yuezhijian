# 下一步开发入口

已完成迭代：`ITER-00`至`ITER-05`。当前主链路为登录→会员→预约→到店→账单→混合支付；会员储值充值和积分流水可独立使用。

## 持续开发顺序

1. 建立次卡类型、适用门店、项目扣次规则、会员次卡余额和不可变扣次流水。
2. 开发次卡定义、售卡、会员次卡详情和项目匹配API及PC页面。
3. 将储值、积分和次卡选项接入结算试算，结算事务原子扣减资产并写流水。
4. 补充账单行编辑删除、优惠分摊、结算黄金样例及资产并发测试。
5. 继续完成换卡、转赠、退卡审批和反向流水。
6. SQL Server镜像就绪后验证8个既有Migration、rowversion及并发入账。

## 下次检查命令

```bash
cd /home/limeng/Documents/code/github/yuezhijian
git status --short
git log -2 --oneline
sed -n '1,260p' memory/DEVELOPMENT_STATUS.md
sed -n '1,200p' memory/NEXT_ACTIONS.md
find backend/src frontend/src -type f | sort
```

若Memory与代码、测试或Git状态冲突，以仓库实际结果为准，并立即修正Memory。
