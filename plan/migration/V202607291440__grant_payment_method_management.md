# V202607291440 支付方式管理权限与并发版本

| 项目 | 内容 |
| --- | --- |
| SQL | `backend/src/main/resources/db/migration/V202607291440__grant_payment_method_management.sql` |
| 需求 | 系统管理-29、优化系统管理-02、API-CAT-019/020、UI-CAT-007 |
| 结构变化 | `cat_payment_method_store`新增`row_version` |
| 权限 | 新增`catalog:payment:view/manage/store-manage` |
| 菜单 | 系统管理新增`/app/system/payment-methods` |

总部管理员获得全部三项权限；店长只获得查看和本店配置权限。应用更新门店适用、启用和排序时必须提交当前rowversion，冲突返回409，不允许最后写入者静默覆盖。

发布后检查门店配置版本、角色授权、菜单显示以及收银端仅返回“全局启用＋本店适用且启用”的支付方式。共享环境不得用`flyway clean`回滚。
