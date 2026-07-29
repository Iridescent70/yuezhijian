# 悦指间系统 API 接口设计

| 项目 | 内容 |
| --- | --- |
| 文档版本 | V1.0 |
| 日期 | 2026-07-29 |
| 接口基线 | `/api/v1` |
| 依据 | 两份合同功能清单、旧系统 JSP/`.do` 入口、Hibernate 映射 |
| 适用端 | PC 管理端、员工移动端、到家客户端、到家店长端、第三方回调 |

本文是首版开发基线。旧系统的 Struts `.do` 入口只用于核对业务，不继续对外提供；新系统统一使用 JSON REST API。接口编号要写入 Swagger `operationId`、开发任务、测试用例和需求追踪表。

## 1. 通用约定

### 1.1 请求与响应

- 协议：HTTPS；字符集 UTF-8；时间为 `yyyy-MM-dd'T'HH:mm:ssXXX`，保存为 UTC，界面按 Asia/Shanghai 展示。
- 鉴权：同源 PC/H5 使用 `HttpOnly + Secure + SameSite` 会话 Cookie 和 CSRF Token；不能可靠使用 Cookie 的移动容器使用与 Spring Session 关联的短期不透明 Bearer Token。系统不自制 JWT。第三方回调使用签名、时间戳和随机数校验。
- 幂等：创建账单、结算、充值、退款、调账、冲销、发券、核销、短信发送和第三方回调必须传 `Idempotency-Key`。
- 并发：可编辑对象返回 `version`，更新时必须回传；版本不一致返回 `40901`。
- 金额：请求和响应均为十进制字符串，如 `"128.50"`，不得使用浮点数；数据库为 `decimal(19,4)`。
- 分页：`page=1&pageSize=20`，最大 200；列表返回 `items/page/pageSize/total`。
- 查询：日期区间含开始、不含结束；总部用户可传 `storeIds`，门店用户由后端强制限定数据范围。
- 删除：业务数据不物理删除，使用停用、取消、作废、冲销等明确动作；只有未被引用的草稿配置允许删除。

统一成功响应：

```json
{
  "code": "0",
  "message": "OK",
  "data": {},
  "traceId": "01J...",
  "serverTime": "2026-07-29T10:00:00+08:00"
}
```

统一失败响应：

```json
{
  "code": "40902",
  "message": "账单当前状态不允许结算",
  "details": [{"field": "status", "reason": "当前为 VOIDED"}],
  "traceId": "01J..."
}
```

| 错误码段 | 含义 | HTTP |
| --- | --- | --- |
| `400xx` | 参数、业务规则或文件格式错误 | 400 |
| `401xx` | 未登录、Token 失效、回调签名错误 | 401 |
| `403xx` | 功能、按钮、数据或敏感字段权限不足 | 403 |
| `404xx` | 对象不存在或已不可见 | 404 |
| `40901` | 乐观锁冲突 | 409 |
| `40902` | 状态不允许 | 409 |
| `40903` | 重复请求或业务唯一键冲突 | 409 |
| `422xx` | 余额、库存、扣次、计算或对账不通过 | 422 |
| `429xx` | 频率或第三方额度限制 | 429 |
| `500xx` | 系统异常；必须返回 traceId | 500 |
| `503xx` | 短信、支付、地图、大模型等外部服务不可用 | 503 |

### 1.2 权限与审计

权限码格式为 `模块:资源:动作`，例如 `trade:bill:settle`。后端逐接口校验功能权限和数据范围，不能只依赖前端隐藏按钮。手机号、卡号、工资、调账金额、账单金额和短信内容根据字段权限脱敏。所有新增、修改、审批、作废、冲销、导入、导出、核销和发送动作写入审计日志，日志保存请求摘要、对象、前后值、结果和 traceId，不记录密码、Token、支付密钥或完整身份证号。

## 2. 认证、权限、组织和公共能力

| API 编号 | 方法与路径 | 用途及主要入参 | 主要返回/权限 | 需求来源 |
| --- | --- | --- | --- | --- |
| API-IAM-001 | `POST /auth/login` | 账号、密码、终端标识、验证码（触发时） | 建立会话，返回用户、门店、菜单及 CSRF 信息；公开 | 通用-登录权限 |
| API-IAM-002 | `POST /auth/session/renew` | 当前有效会话 | 延长会话并轮换标识 | 通用-登录权限 |
| API-IAM-003 | `POST /auth/logout` | 当前会话 | 注销结果 | 通用-登录权限 |
| API-IAM-004 | `GET /auth/me` | 无 | 用户、角色、数据范围、字段权限 | 通用-登录权限 |
| API-IAM-005 | `PUT /auth/password` | oldPassword/newPassword | 修改结果；`iam:self:password` | 系统管理-08 |
| API-IAM-006 | `GET /users` | 账号、姓名、门店、状态 | 用户分页；`iam:user:view` | 系统管理-04 |
| API-IAM-007 | `POST /users` | 账号、姓名、员工、角色、门店 | userId；`iam:user:create` | 系统管理-04 |
| API-IAM-008 | `GET/PUT /users/{id}` | 用户详情/可变字段、version | 用户详情/更新结果 | 系统管理-04 |
| API-IAM-009 | `POST /users/{id}/lock`、`/unlock` | 原因 | 状态结果 | 系统管理-04 |
| API-IAM-010 | `POST /users/{id}/reset-password` | 临时密码策略 | 重置结果，不返回明文 | 系统管理-04 |
| API-IAM-011 | `GET/POST /roles` | 查询/角色名称、代码、数据范围 | 角色列表/id | 系统管理-05 |
| API-IAM-012 | `GET/PUT /roles/{id}` | 详情/角色信息、version | 详情/更新结果 | 系统管理-05 |
| API-IAM-013 | `PUT /roles/{id}/permissions` | menuIds、permissionCodes、fieldRules | 保存结果 | 系统管理-05 |
| API-IAM-014 | `GET /menus/tree` | clientType、enabled | 菜单树 | 系统管理-06 |
| API-IAM-015 | `POST/PUT /menus`、`/menus/{id}` | 父级、路由、图标、排序、权限码 | menuId/更新结果 | 系统管理-06 |
| API-IAM-016 | `PUT /menus/sort` | 父级下有序 id | 保存结果 | 系统管理-06 |
| API-ORG-001 | `GET /organizations/tree` | 类型、状态 | 公司/区域/门店树 | 多门店通用 |
| API-ORG-002 | `POST /organizations` | 上级、类型、编码、名称 | organizationId | 多门店通用 |
| API-ORG-003 | `GET/PUT /organizations/{id}` | 详情/资料、version | 详情/更新结果 | 多门店通用 |
| API-ORG-004 | `GET/POST /stores` | 门店查询/编码、名称、地址、等级 | 门店列表/id | 多门店通用 |
| API-ORG-005 | `GET/PUT /stores/{id}` | 详情/资料、营业时间、状态 | 门店详情/更新结果 | 多门店通用 |
| API-ORG-006 | `GET/POST /positions` | 查询/职务、代码、默认提成 | 列表/id | 系统管理-10 |
| API-ORG-007 | `PUT /positions/{id}` | 职务资料、version | 更新结果 | 系统管理-10 |
| API-ORG-008 | `GET/POST /employees` | 查询/人员、职务、主门店、入职信息 | 分页/id | 系统管理-09 |
| API-ORG-009 | `GET/PUT /employees/{id}` | 详情/资料、状态、version | 员工详情/更新结果 | 系统管理-09 |
| API-ORG-010 | `POST /employees/import` | 文件 id | 导入任务 id | 系统管理-09 |
| API-ORG-011 | `GET/POST /employee-loans` | 查询/员工、原店、借调店、期间、比例 | 分页/id | 系统管理-37、薪酬-03 |
| API-ORG-012 | `POST /employee-loans/{id}/approve`、`/reject` | 意见 | 审批结果 | 系统管理-37、薪酬-03 |
| API-ORG-013 | `GET/POST /workstations` | 门店、状态/名称、容量 | 工位列表/id | 系统管理-14 |
| API-ORG-014 | `GET/POST /terminals` | 门店/设备指纹、名称、状态 | 终端列表/id | 系统管理-15 |
| API-COM-001 | `POST /files` | multipart 文件、用途 | fileId、受控下载地址 | 通用-导入导出 |
| API-COM-002 | `GET /files/{id}` | fileId | 文件元数据/文件流 | 通用-导入导出 |
| API-COM-003 | `POST /exports` | exportType、filters、columns | 异步任务 id | 系统管理-01 |
| API-COM-004 | `GET /jobs/{id}` | 任务 id | 进度、成功/失败数、文件 id | 系统管理-01 |
| API-COM-005 | `GET /jobs` | 类型、状态、创建人、日期 | 下载中心分页 | 系统管理-01 |
| API-COM-006 | `GET /audit-logs` | 用户、模块、动作、对象、日期 | 审计分页 | 系统管理-07 |
| API-COM-007 | `GET /audit-logs/{id}` | 日志 id | 前后值摘要、结果、traceId | 系统管理-07 |
| API-COM-008 | `GET /system-parameters`、`PUT /system-parameters/{id}` | `group`/`value/status/version` | 非密钥参数列表/更新结果；`system:parameter:view/manage`；已实现 | 系统管理-17 |
| API-COM-009 | `GET/POST /cancel-reasons` | 业务类型/代码、名称、状态 | 列表/id | 系统管理-31 |
| API-COM-010 | `GET /operation-history/{objectType}/{objectId}` | 对象类型/id | 业务变更时间线 | 通用-历史追踪 |

## 3. 商品、服务、次卡、库存和门店配置

| API 编号 | 方法与路径 | 用途及主要入参 | 主要返回/权限 | 需求来源 |
| --- | --- | --- | --- | --- |
| API-CAT-001 | `GET/POST /item-categories` | 类型、父级/分类资料 | 分类树/id | 系统管理-11、21 |
| API-CAT-002 | `PUT /item-categories/{id}` | 名称、排序、状态、version | 更新结果 | 系统管理-11、21 |
| API-CAT-003 | `GET/POST /units` | 查询/编码、名称、精度 | 列表/id | 系统管理-12 |
| API-CAT-004 | `GET/POST /products` | 多条件查询/产品、单位、成本、售价 | 分页/id | 系统管理-11 |
| API-CAT-005 | `GET/PUT /products/{id}` | 详情/产品资料、version | 详情/更新结果 | 系统管理-11 |
| API-CAT-006 | `POST /products/batch-status` | ids、ON_SALE/OFF_SALE | 批量结果 | 优化系统管理-01 |
| API-CAT-007 | `POST /products/import`、`POST /products/export` | 文件/筛选条件 | 异步任务 id | 系统管理-11 |
| API-CAT-008 | `GET/POST /services` | 查询/项目、时长、售价、成本、分类 | 分页/id | 系统管理-21、优化系统管理-01 |
| API-CAT-009 | `GET/PUT /services/{id}` | 详情/门店、标签、物料消耗、version | 详情/更新结果 | 系统管理-21、优化系统管理-01 |
| API-CAT-010 | `POST /services/batch-update` | ids、分类/标签/门店/状态 | 批量结果 | 优化系统管理-01 |
| API-CAT-011 | `POST /services/import`、`POST /services/export` | 文件/筛选条件 | 任务 id | 优化系统管理-01 |
| API-CAT-012 | `GET/POST /card-types` | 查询/名称、售价、总次、有效期、门店 | 分页/id | 系统管理-13、次卡管理-05 |
| API-CAT-013 | `GET/PUT /card-types/{id}` | 详情/分组、说明、状态、version | 详情/更新结果 | 系统管理-13、次卡管理-05 |
| API-CAT-014 | `POST /card-types/{id}/copy` | 新名称、适用门店 | 新 cardTypeId | 次卡管理-05 |
| API-CAT-015 | `POST /card-types/batch-update` | ids、分类/门店/状态 | 批量结果 | 次卡管理-05 |
| API-CAT-016 | `GET/PUT /card-types/{id}/service-rules` | 无/服务 id、可扣次数、换算规则 | 规则 | 结算管理-01 |
| API-CAT-017 | `GET/PUT /card-types/{id}/commission-rules` | 无/售卡与消耗提成规则 | 规则 | 次卡管理-01 |
| API-CAT-018 | `GET /card-types/{id}/preview` | 门店 | 购买页预览 | 次卡管理-05 |
| API-CAT-019 | `GET/POST /payment-methods` | 门店、类型/代码、名称、统计标志 | 列表/id | 系统管理-29、优化系统管理-02 |
| API-CAT-020 | `PUT /payment-methods/{id}`、`PUT /payment-methods/sort` | 配置/version、排序 | 更新结果 | 系统管理-29、优化系统管理-02 |
| API-CAT-021 | `GET/POST /vouchers` | 关键词和状态查询；编码、名称、金额/折扣、门槛、有效天数和提成口径 | 定义列表/详情；`benefit:voucher:view/manage` | 系统管理-16 |
| API-CAT-022 | `GET/PUT /vouchers/{id}` | 详情；规则、状态和`version` | 详情/并发安全更新结果；`benefit:voucher:view/manage` | 系统管理-16 |
| API-CAT-023 | `GET/POST /gifts` | 查询/编码、名称、积分价、状态 | 分页/id | 系统管理-23~26 |
| API-INV-001 | `GET /inventories` | 门店、物品、低库存 | 现存量分页 | 系统管理-25 |
| API-INV-002 | `GET/POST /inventory-transfers` | 查询/调出入门店、明细 | 单据分页/id | 系统管理-23 |
| API-INV-003 | `POST /inventory-transfers/{id}/confirm`、`/void`、`/reverse` | 原因 | 库存流水及状态 | 系统管理-23 |
| API-INV-004 | `GET/POST /inventory-counts` | 查询/门店、盘点范围 | 盘点单分页/id | 系统管理-24 |
| API-INV-005 | `PUT /inventory-counts/{id}/lines` | 实盘数、version | 差异汇总 | 系统管理-24 |
| API-INV-006 | `POST /inventory-counts/{id}/confirm` | 确认说明 | 库存调整流水 | 系统管理-24 |
| API-EQP-001 | `GET/POST /equipments` | 门店、状态/编号、型号、责任人 | 分页/id | 系统管理-27 |
| API-EQP-002 | `GET/PUT /equipments/{id}` | 详情/资料、version | 详情/更新 | 系统管理-27 |
| API-EQP-003 | `GET/POST /equipment-receipts` | 查询/设备、领取人、日期 | 领取记录/id | 系统管理-28 |
| API-EQP-004 | `POST /equipment-receipts/{id}/return` | 归还时间、状态、说明 | 归还结果 | 系统管理-28 |
| API-CFG-001 | `GET/POST /service-areas` | 门店/城市、区域、地址、经纬度、半径、上门费 | 列表/id | 系统管理-30、优化系统管理-03 |
| API-CFG-002 | `GET/PUT /service-areas/{id}` | 详情/资料、version | 详情/更新 | 系统管理-30、优化系统管理-03 |
| API-CFG-003 | `GET/POST /receipt-templates` | 门店/名称、组件 JSON、状态 | 列表/id | 优化系统管理-04 |
| API-CFG-004 | `GET/PUT /receipt-templates/{id}` | 详情/组件、变量、version | 模板/更新 | 优化系统管理-04 |
| API-CFG-005 | `POST /receipt-templates/{id}/preview` | 样例账单 id | 预览 HTML/PDF | 优化系统管理-04 |
| API-CFG-006 | `GET/POST /banners` | 位置、状态/图片、链接、有效期 | 列表/id | 系统管理-32 |
| API-CFG-007 | `GET/PUT /color-styles` | 分类/色号、素材、状态 | 在线试色数据 | 系统管理-22 |

## 4. 会员、资产、标签和权益

| API 编号 | 方法与路径 | 用途及主要入参 | 主要返回/权限 | 需求来源 |
| --- | --- | --- | --- | --- |
| API-MEM-001 | `GET /members` | 关键词、卡号、手机号、等级、标签、门店、资产、到店频次、状态 | 会员分页；`member:view` | 会员管理-01、优化会员-01 |
| API-MEM-002 | `POST /members` | 姓名、手机、性别、生日、入会店、顾问、来源 | memberId、会员卡号 | 会员管理-01、快捷入口-01 |
| API-MEM-003 | `GET/PUT /members/{id}` | 详情/姓名、昵称、可选新手机号、性别、生日、邮箱、顾问、特殊标记、version | 聚合详情/更新后详情；手机号不回显明文；`member:member:view/manage`；已实现 | 会员管理-01、优化会员-02 |
| API-MEM-004 | `POST /members/{id}/status` | `ACTIVE/FROZEN/INACTIVE`、必填原因、version | 更新后详情；写状态历史；`member:member:manage`；已实现 | 会员管理-01、优化会员-06 |
| API-MEM-005 | `POST /members/batch-freeze` | memberIds 或筛选快照、原因 | 任务 id | 优化会员-06 |
| API-MEM-006 | `GET /members/{id}/assets` | 无 | 储值、积分、次卡、券汇总 | 优化会员-02 |
| API-MEM-007 | `GET /members/{id}/transactions` | 类型、日期、门店 | 消费和资产流水 | 会员管理-01 |
| API-MEM-008 | `GET/POST /members/{id}/notes` | 查询/类型、内容、负向标志 | 跟进记录/id | 会员管理-01、优化会员-03 |
| API-MEM-009 | `GET/PUT /members/{id}/consultation-card` | 无/结构化咨询卡、version | 咨询卡 | 结算管理-05、移动端-03 |
| API-MEM-010 | `GET/PUT /members/{id}/service-profile` | 无/偏好、禁忌、色号、照片 | 服务档案 | 优化会员-02 |
| API-MEM-011 | `GET /ownership-adjustments`、`GET /ownership-adjustments/{id}`、`POST /members/{id}/ownership-adjustments` | 会员/审批/执行状态查询；新门店、生效日、分润规则JSON快照、原因、memberVersion | 列表/详情/申请；`member:ownership:view/manage`；已实现 | 优化会员-05 |
| API-MEM-012 | `POST /ownership-adjustments/{id}/approve`、`/reject` | 意见、version；驳回意见必填 | 审批与执行结果；`member:ownership:approve`；已实现 | 优化会员-05 |
| API-MEM-013 | `GET/POST /member-levels` | 查询/代码、名称、储值门槛、生日优惠 | 列表/id | 会员管理-02 |
| API-MEM-014 | `GET/PUT /member-levels/{id}` | 详情/规则、状态、version | 详情/更新 | 会员管理-02 |
| API-MEM-015 | `GET/POST /member-tags` | 类型/名称、规则、颜色、自动标志 | 已实现GET启用标签选项；POST配置接口待标签规则页迭代；`member:tag:view` | 优化会员-03 |
| API-MEM-016 | `PUT /members/{id}/tags` | `addIds/removeIds/version`；仅启用标签 | 更新后会员详情；保留分配/移除历史；`member:tag:manage`；已实现 | 优化会员-03 |
| API-MEM-017 | `POST /members/tags/batch` | memberIds、addIds/removeIds | 批量结果 | 优化会员-01、03 |
| API-MEM-018 | `GET/POST /member-segments` | 查询/名称、筛选条件 JSON | 客群视图/id | 优化会员-01 |
| API-MEM-019 | `POST /member-segments/{id}/preview` | 无 | 命中数和样例 | 优化会员-01 |
| API-MEM-020 | `POST /members/batch-assign-advisor` | memberIds、employeeId | 批量结果 | 优化会员-01 |

`API-MEM-003~004、011~012、015(GET)、016`已落地。资料、状态、归属申请和标签修改都要求提交版本，过期版本返回冲突，避免多窗口覆盖。手机号留空表示不修改；填写新号码时重新加密并生成检索哈希，响应始终只返回尾号。冻结、解冻和停用都必须填写原因，当前状态写在会员主表，完整变更写入`mem_member_status_log`。标签分配采用追加与软移除，不覆盖原来源。批量冻结、标签定义配置和批量顾问仍按各自接口继续开发。

归属调整不允许填写历史日期，也不在普通会员编辑中直接改门店。同日申请审批通过后立即执行，未来日期保持`APPROVED/WAITING`并由运行档定时任务到期领取；执行只修改会员当前归属并清空原门店顾问，历史账单、提成和业绩快照不变。同一会员只允许一张`WAITING/PROCESSING`申请。`shareRule`仅保存甲方确认的JSON快照，当前不宣称已完成第三方分润计算。

| API-AST-001 | `GET /members/{id}/balance-account` | 无 | 可用、冻结、累计储值 | 会员资产 |
| API-AST-002 | `POST /members/{id}/recharges/quote` | 金额、赠送、支付方式 | 试算结果 | 结算管理-01、移动端-04 |
| API-AST-003 | `POST /members/{id}/recharges` | quoteId、收款、门店、销售员工 | 充值单 id、待确认状态 | 会员资产 |
| API-AST-004 | `POST /recharges/{id}/confirm`、`/cancel` | 确认或取消原因 | 账户流水 | 移动端-04 |
| API-AST-005 | `GET /members/{id}/balance-ledgers` | 日期、类型 | 储值流水 | 会员资产 |
| API-AST-006 | `GET /members/{id}/point-account` | 无 | 可用/累计积分 | 会员资产 |
| API-AST-007 | `GET /members/{id}/point-ledgers` | 日期、类型 | 积分流水 | 会员资产 |
| API-AST-008 | `GET /members/{id}/cards` | 状态、到期范围 | 会员次卡列表 | 次卡全生命周期 |
| API-AST-009 | `GET /member-cards/{id}` | 次卡 id | 次卡、项目余额、来源和流水 | 次卡全生命周期 |
| API-AST-010 | `POST /members/{id}/cards` | cardTypeId、数量、有效期、销售员工、支付 | 购卡单和次卡 id | 次卡全生命周期 |
| API-AST-011 | `POST /member-cards/{id}/exchange/quote` | `targetCardTypeId`；按原办卡金额÷原总次数×剩余次数折算，报价10分钟有效 | 原卡余次/余值、目标卡售价、补差、原卡及卡种版本；`member:card:manage` | 次卡管理-02 |
| API-AST-012 | `POST /member-cards/{id}/exchange` | `quoteNo`、门店、经办员工、补差支付明细、幂等键 | 原卡`EXCHANGED`、新卡、双向流水和换卡单；`member:card:manage` | 次卡管理-02 |
| API-AST-013 | `POST /member-cards/{id}/transfer` | 接收会员、转赠后有效期、经办门店/员工、原因、原卡版本、幂等键 | 原卡`TRANSFERRED`、接收会员新卡、双方流水和转赠单；`member:card:manage` | 次卡管理-03 |
| API-AST-014 | `POST /member-cards/{id}/refund-requests/quote`、`POST /member-cards/{id}/refund-requests` | 手续费试算；quoteNo、退款方式、经办门店/员工、原因、幂等键 | 消费项目原价重计明细；提交后卡冻结并生成申请；`member:card:refund:manage` | 次卡管理-04 |
| API-AST-015 | `GET /card-refund-requests`、`GET /card-refund-requests/{id}`、`POST /card-refund-requests/{id}/review` | 状态筛选；通过/驳回、意见、version | 申请详情和审批结果；驳回恢复卡；查看/审批权限分离 | 次卡管理-04 |
| API-AST-016 | `POST /card-refund-requests/{id}/execute` | version、外部退款凭证、执行幂等键 | 卡清零、`REFUND_OUT`流水和退款事实；沿转赠谱系冲回原售卡提成，返回`COMPLETED/PENDING_MODULE/NOT_APPLICABLE` | 次卡管理-04 |
| API-AST-017 | `GET /member-cards/{id}/ledgers` | 类型、日期 | 扣次、换卡、转赠、退卡流水 | 次卡管理-02~04 |
| API-AST-018 | `POST /members/{id}/points/adjustments` | 正负积分、原因、幂等键 | 最新积分账户；`member:asset:manage` | 会员资产人工调账 |
| API-BEN-001 | `POST /points/redemptions/quote` | memberId、giftLines | 积分和库存校验 | 快捷入口-07 |
| API-BEN-002 | `POST /points/redemptions` | quoteId、门店 | 兑换单、积分/库存流水 | 系统管理-26、快捷入口-07 |
| API-BEN-003 | `POST /point-redemptions/{id}/void`、`/reverse` | 原因 | 反向流水 | 系统管理-26 |
| API-BEN-004 | `GET /voucher-codes`、`GET /voucher-codes/{code}` | 会员、状态、关键词/券码 | 发放规则快照、会员、有效期、核销账单和版本；`benefit:voucher:view` | 快捷入口-08 |
| API-BEN-005 | `POST /voucher-code-issues` | voucherId、数量、可选memberId、幂等键 | 1~100张券码；可直接绑定会员；`benefit:voucher:issue` | 系统管理-16、优化会员-01 |
| API-BEN-006 | `POST /voucher-codes/{code}/bind` | memberId、幂等键 | 仅`UNBOUND → BOUND`；`benefit:voucher:issue` | 快捷入口-08 |
| API-BEN-007 | `POST /service-code-jobs` | 类别、数量、有效期、渠道、提成规则 | 生成任务 id | 系统管理-02 |
| API-BEN-008 | `GET /service-codes` | 批次、码、会员、状态、门店 | 服务码分页 | 系统管理-03 |
| API-BEN-009 | `POST /service-codes/{code}/bind`、`/redeem` | 会员、门店、服务 | 绑定/核销流水 | 快捷入口-09 |

## 5. 预约、账单、结算、调账和冲销

| API 编号 | 方法与路径 | 用途及主要入参 | 主要返回/权限 | 需求来源 |
| --- | --- | --- | --- | --- |
| API-APT-001 | `GET /appointments/calendar` | 门店、日期、员工、工位、状态 | 日/周排期和占用时段 | 业务管理-03、预约-01~03 |
| API-APT-002 | `GET /appointments/availability` | 门店、服务、员工、日期 | 可预约时段和候补提示 | 预约-01、02 |
| API-APT-003 | `POST /appointments` | 会员/散客、门店、来源、项目、技师、起止、备注 | appointmentId | 业务管理-03、快捷入口-05 |
| API-APT-004 | `GET/PUT /appointments/{id}` | 详情/时间、项目、技师、工位、version | 详情/更新 | 预约管理 |
| API-APT-005 | `POST /appointments/{id}/confirm` | 无 | CONFIRMED | 预约-03 |
| API-APT-006 | `POST /appointments/{id}/arrive` | 到店人数、时间 | ARRIVED；可创建待结算账单 | 预约-03、04 |
| API-APT-007 | `POST /appointments/{id}/start`、`/complete` | 实际时间 | SERVING/COMPLETED | 预约-03 |
| API-APT-008 | `POST /appointments/{id}/cancel` | 原因 id、说明 | CANCELLED、释放时段 | 预约-03、04 |
| API-APT-009 | `POST /appointments/{id}/no-show` | 原因、说明 | NO_SHOW、累计爽约 | 预约-05 |
| API-APT-010 | `POST /appointments/{id}/create-bill` | 是否复制预约项目 | billId | 预约-04 |
| API-APT-011 | `GET/POST /schedules` | 员工、门店、期间/班次和休息日 | 排班列表/id | 预约-01 |
| API-APT-012 | `GET/POST /waitlist` | 日期、服务/会员、期望时段 | 候补列表/id | 预约-02 |
| API-APT-013 | `POST /waitlist/{id}/convert` | slotId | appointmentId | 预约-02 |
| API-TRD-001 | `GET /invitation-reminders` | 门店、账单状态、活跃度、最后消费日 | 邀约提醒分页 | 业务管理-01 |
| API-TRD-002 | `GET /bills` | 编号、会员、门店、状态、来源、日期 | 账单分页 | 业务管理-02、快捷入口-04 |
| API-TRD-003 | `POST /bills` | 会员/散客、门店、来源、人数、备注 | 草稿 billId | 结算管理-06、快捷入口-03 |
| API-TRD-004 | `GET/PUT /bills/{id}` | 聚合详情/基础字段、version | 账单、明细、支付、状态 | 结算管理-06 |
| API-TRD-005 | `POST /bills/{id}/lines` | 项目/产品/次卡、数量、原价、技师及分配 | 行 id、金额重算 | 结算管理-06 |
| API-TRD-006 | `PUT/DELETE /bills/{id}/lines/{lineId}` | PUT：数量、技师、备注、`version`；DELETE：`version` | 最新账单；删除为软删除 | 结算管理-06 |
| API-TRD-007 | `POST /bills/{id}/discounts` | `discountType=AMOUNT/RATE`、`value`、原因、`version` | 按行分摊后的账单和优惠明细 | 混合支付 |
| API-TRD-008 | `GET /bills/{id}/asset-options`、`/card-options` | 账单会员和明细 | 储值/积分、次卡推荐及满足会员/有效期/门槛的代金券 | 结算管理-01、02 |
| API-TRD-009 | `POST /bills/{id}/settlement/quote` | `payments`、`balanceAmount`、`points`、`cards[]`、`voucherCodeIds[]` | 金额、券规则快照、全部资产版本及10分钟有效试算 | 结算管理-01、02 |
| API-TRD-010 | `POST /bills/{id}/settle` | `quoteNo`、`idempotencyKey` | SETTLED、外部支付及储值/积分/次卡/代金券不可变流水 | 业务管理-02、结算-01、02 |
| API-TRD-011 | `POST /bills/{id}/void` | 原因；仅允许未结算 | VOIDED | 业务管理-02 |
| API-TRD-012 | `GET /bills/{id}/receipt` | templateId | 小票数据/打印内容 | 优化系统管理-04 |
| API-TRD-013 | `GET/POST /bill-adjustments` | 查询/原账单、调整项、原因、证明文件 | 申请分页/id | 结算管理-03 |
| API-TRD-014 | `GET /bill-adjustments/{id}/preview` | 申请 id | 新旧金额、门店和提成差额 | 结算管理-03 |
| API-TRD-015 | `POST /bill-adjustments/{id}/approve`、`/reject` | 意见 | 审批结果 | 结算管理-03 |
| API-TRD-016 | `POST /bill-adjustments/{id}/execute` | 幂等键 | 调整单、反向及新流水 | 结算管理-03 |
| API-TRD-017 | `GET /reversals`、`POST /bills/{billId}/reversals` | 状态查询/原因、`idempotencyKey` | 列表/冲销申请、支付及资产影响；`trade:reversal:view/manage` | 结算管理-04 |
| API-TRD-018 | `GET /reversals/{id}` | 冲销 id | 申请、原账单、支付退款及资产返还明细；`trade:reversal:view` | 结算管理-04 |
| API-TRD-019 | `POST /reversals/{id}/review` | `approved`、意见、`version` | `APPROVED/REJECTED`；`trade:reversal:approve` | 结算管理-04 |
| API-TRD-020 | `POST /reversals/{id}/execute` | `version`、`idempotencyKey` | `EXECUTED`、账单`REVERSED`、支付退款及储值/积分/次卡/代金券反向流水；`trade:reversal:manage` | 业务管理-02、结算管理-04 |
| API-TRD-021 | `GET /recommendation-card-performance` | 日期、门店、技师 | 推荐卡卡数和金额 | 业务管理-04 |
| API-TRD-022 | `GET /global-search` | keyword、types | 会员、账单、预约、功能结果 | 优化系统管理-05 |

账单状态：`DRAFT → PENDING_PAYMENT → SETTLED`；草稿可 `VOIDED`，已结算只能通过审批生成 `ADJUSTED` 或 `REVERSED`，禁止直接改状态。预约状态：`PENDING_CONFIRM → CONFIRMED → ARRIVED → SERVING → COMPLETED`，分支为 `CANCELLED/NO_SHOW`。审批状态统一为 `DRAFT/SUBMITTED/APPROVING/APPROVED/REJECTED/EXECUTED/CANCELLED`。

当前冲销接口先落地已结算账单的整单冲销：申请时按账单事实生成支付和会员资产影响，审批后一次性执行；现金找零从退款金额中扣除，原账单金额和原流水保留。储值、积分和次卡分别追加`REFUND`流水并关联原扣减流水。部分退款、储值单/次卡单独冲销、多级审批、提成冲回及真实电子支付通道退款属于后续扩展；通道未接通前，电子支付退款记录仅可用于本地联调，不能作为生产退款验收结果。

## 6. 回访、满意度、短信、通知和营销

| API 编号 | 方法与路径 | 用途及主要入参 | 主要返回/权限 | 需求来源 |
| --- | --- | --- | --- | --- |
| API-VIS-001 | `GET /visit-tasks` | `storeId/employeeId/status/dueDate/keyword`；状态为`PENDING/OVERDUE/COMPLETED/CANCELLED` | 任务、会员、账单、技师进度、客诉及到期时间；`visit:task:view`；已实现 | 会员管理-03、结算管理-05 |
| API-VIS-002 | `GET /visit-tasks/{id}` | id | 任务、账单、会员、参与技师和回访历史；`visit:task:view`；已实现 | 会员管理-03 |
| API-VIS-003 | `POST /visit-tasks/{id}/records` | `employeeId/resultCode/satisfactionScore/complaintFlag/content/nextFollowAt` | 更新后的完整任务；`visit:task:manage`；已实现 | 结算管理-05 |
| API-VIS-004 | `POST /visit-tasks/{id}/complete` | `conclusion`；仅所有参与技师完成后允许 | 完成状态和总结；`visit:task:manage`；已实现 | 结算管理-05 |
| API-VIS-005 | `GET/POST /satisfaction-rules`、`PUT /satisfaction-rules/{id}`、`POST /satisfaction-rules/test` | 状态/名称、字面关键词、1~5分、组件键值映射、优先级、状态、version；试算文本 | 规则列表/保存结果/首条命中结果；`visit:satisfaction:view/manage`；已实现 | 系统管理-19 |
| API-VIS-006 | `GET /service-feedback`、`GET /service-feedback/{id}` | `storeId/handlerId/score/status/keyword`；详情 id | 反馈列表/反馈、会员、账单、负责人和处理历史；`visit:feedback:view`；已实现 | 系统管理-20 |
| API-VIS-007 | `POST /service-feedback/{id}/handle` | `action/handlerId/content/result`；动作`ASSIGN/NOTE/RESOLVE/CLOSE/REOPEN` | 更新后的反馈和处理历史；`visit:feedback:manage`；已实现 | 系统管理-20 |
| API-MKT-001 | `POST /sms-tasks` | 名称、号码/客群、内容、sendAt | taskId、预估条数 | 短信-01、05 |
| API-MKT-002 | `POST /sms-tasks/import` | Excel fileId、模板 id、sendAt | 导入/发送任务 id | 短信-02 |
| API-MKT-003 | `GET /sms-tasks` | 批次、状态、日期、创建人 | 任务分页 | 短信-03 |
| API-MKT-004 | `GET /sms-tasks/{id}` | id | 内容、数量、进度、费用和失败摘要 | 短信-03 |
| API-MKT-005 | `GET /sms-messages` | taskId、手机号、状态、到达状态 | 发送明细 | 短信-03 |
| API-MKT-006 | `GET /sms-inbounds` | 手机、关键字、接收时间 | 上行短信分页 | 短信-04 |
| API-MKT-007 | `POST /sms-inbounds/export` | filters、选中 ids | 导出任务 id | 短信-04 |
| API-MKT-008 | `GET /sms-templates` | 类型、状态 | 模板列表 | 短信通道 |
| API-MKT-009 | `POST /members/batch-issue-voucher` | memberIds、voucherId、数量 | 批量发券任务 | 优化会员-01 |
| API-NTF-001 | `GET /notifications` | 类型、已读状态、日期 | 当前用户消息分页 | 通知-01 |
| API-NTF-002 | `POST /notifications/{id}/read`、`POST /notifications/read-all` | id/范围 | 已读结果 | 通知-01 |
| API-NTF-003 | `GET/POST /notification-templates` | 查询/事件、渠道、标题、正文、变量 | 模板列表/id | 通知-01 |
| API-NTF-004 | `PUT /notification-templates/{id}` | 模板、状态、version | 更新结果 | 通知-01 |
| API-NTF-005 | `POST /notifications/test` | templateId、channel、recipient | 测试发送结果 | 通知-01 |
| API-NTF-006 | `GET/POST /announcements` | 查询/标题、内容、门店、有效期 | 公告列表/id | 系统管理-18 |
| API-NTF-007 | `PUT /announcements/{id}` | 公告内容、状态、version | 更新结果 | 系统管理-18 |

`API-VIS-001~005`基础闭环已落地。会员账单确认结算后在同一事务中生成一张回访任务，同一账单重复结算不会重复生成。到期时间读取`VISIT/AFTER_SALE_DUE_HOURS`系统参数，默认24小时，参数只影响新任务。每位服务技师生成一个参与项，无技师账单生成“待分配”项。`CONTACTED`必须填写1至5分满意度，`NO_ANSWER/FOLLOW_UP`必须填写未来的下次跟进时间，`DECLINED`直接结束该技师参与项；所有参与项完成后任务自动完成。整单冲销只取消尚未完成的任务。

满意度规则按优先级从小到大执行字面包含匹配，同一规则内优先测试较长关键词；不执行正则或大模型推断，未命中时不生成分值。组件映射保存为甲方定义的字符串键值。样例试算只返回命中结果，不写会员、回访或短信数据；自动识别短信须等上行短信通道接入。

`API-VIS-006~007`客诉闭环已落地。只有回访人员明确勾选客诉时才自动建反馈单，低评分不会擅自转客诉；一条回访记录最多一张反馈单。客诉未评分时不伪造分值。状态按`OPEN → PROCESSING → RESOLVED → CLOSED`流转，已解决或已关闭可以`REOPEN`回到处理中。分配、备注、解决、关闭和重开均追加`vis_feedback_action`，不覆盖原客诉内容。附件和非回访渠道反馈仍待文件模块及甲方渠道口径确认。

## 7. 薪酬、提成、目标和分润

| API 编号 | 方法与路径 | 用途及主要入参 | 主要返回/权限 | 需求来源 |
| --- | --- | --- | --- | --- |
| API-COMM-001 | `GET/POST /commission-plans` | 查询/编码、名称、场景、基础模式、适用门店/职务、有效期 | 列表/详情；已实现 | 系统管理-34、薪酬-01 |
| API-COMM-002 | `GET/PUT /commission-plans/{id}` | 详情/基础规则、状态、version | 详情/更新；已实现 | 系统管理-34、薪酬-01 |
| API-COMM-003 | `POST /commission-plans/{id}/simulate` | `employeeId/storeId/businessDate/performanceAmount/itemCount` | 方案版本、适用性、告警、逐步公式和结果；只读不写流水；`commission:plan:view`；已实现 | 薪酬-01 |
| API-COMM-004 | `GET /commission-ledgers` | 员工、门店、期间、正负向、计算状态 | 提成流水；服务账单和冲销已实现 | 薪酬全模块 |
| API-COMM-005 | `GET /commission-ledgers/{id}/calculation` | 流水 id | 公式、参数、阶梯和结果 | 统计分析优化-04 |
| API-COMM-006 | `GET/POST /third-party-share-rules` | 查询/项目、第三方、比例、有效期 | 规则列表/id | 薪酬-02 |
| API-COMM-007 | `GET /third-party-share-ledgers` | 第三方、门店、期间、结算状态 | 分润流水 | 薪酬-02 |
| API-COMM-008 | `POST /third-party-settlements` | 第三方、期间、流水 ids | 结算单 id | 薪酬-02 |
| API-COMM-009 | `GET/POST /employee-targets` | 查询/员工、门店、目标类型、周期和值 | 目标分页/id | 系统管理-33、薪酬-04 |
| API-COMM-010 | `GET /employee-targets/progress` | 员工/门店、期间、类型 | 完成值和完成率 | 薪酬-04 |
| API-COMM-011 | `GET/POST /technician-adjustments` | 查询/员工、期间、类型、金额、原因 | 申请分页/id | 系统管理-35 |
| API-COMM-012 | `POST /technician-adjustments/{id}/approve`、`/void` | 意见/原因 | 状态和提成流水 | 系统管理-35 |
| API-COMM-013 | `GET/POST /manager-adjustments` | 查询/门店、期间、指标、金额、原因 | 申请分页/id | 系统管理-36 |
| API-COMM-014 | `POST /manager-adjustments/{id}/approve`、`/void` | 意见/原因 | 状态和流水 | 系统管理-36 |
| API-COMM-015 | `POST /payroll-runs` | 工资期间、门店、员工范围 | 计算批次 id | 统计分析-09、27 |
| API-COMM-016 | `GET /payroll-runs/{id}` | 批次 id | 进度、汇总、异常 | 统计分析-09、27 |
| API-COMM-017 | `GET /payroll-runs/{id}/employees/{employeeId}` | 员工 | 工资项和提成来源明细 | 统计分析-27 |
| API-COMM-018 | `POST /payroll-runs/{id}/confirm` | 说明 | 锁定工资单 | 统计分析-27 |

`API-COMM-001~004`基础版已落地。基础计算方式为`RATE/FIXED/NONE`；普通服务、售卡、次卡实耗分别匹配`SERVICE/CARD_SALE/CARD_CONSUME`，未匹配规则时生成`PENDING_RULE`事实。账单冲销、换卡和退卡追加关联原流水的负向记录，转赠后换卡/退卡沿卡谱系追溯原售卡事实。六种完整模式、累计阶梯、多人分配、人工调账和工资批次仍需后续实施，当前不得用基础比例替代甲方未确认口径。

## 8. 到家服务与移动端

移动端复用前述会员、预约、账单和审批接口，后端根据客户端和权限裁剪字段；不另建一套业务数据。

| API 编号 | 方法与路径 | 用途及主要入参 | 主要返回/权限 | 需求来源 |
| --- | --- | --- | --- | --- |
| API-MOB-001 | `GET /mobile/workbench` | 门店、日期 | 今日预约/客量/营业额、常用入口 | 移动端、优化系统管理-05 |
| API-MOB-002 | `GET /mobile/appointments/today` | 状态 | 今日预约 | 移动端-02 |
| API-MOB-003 | `GET /mobile/members/{id}/summary` | memberId | 脱敏聚合资料 | 移动端-03 |
| API-MOB-004 | `GET /mobile/pending-recharges` | 门店 | 待确认充值 | 移动端-04 |
| API-MOB-005 | `GET /mobile/pending-approvals` | 类型 | 当前可审任务 | 移动端-05 |
| API-HOME-001 | `GET /home/landing` | 定位、门店 | 服务范围、Banner、快捷入口、热门服务和技师 | 到家服务-01 |
| API-HOME-002 | `GET /home/search` | keyword、lat、lng | 服务和技师 | 到家服务-01 |
| API-HOME-003 | `GET /home/services/{id}` | 服务 id、定位 | 服务详情、价格和可服务门店 | 到家服务-01 |
| API-HOME-004 | `GET /home/technicians/{id}` | 技师 id | 介绍、项目、可约时段 | 到家服务-01 |
| API-HOME-005 | `POST /home/orders/quote` | 地址、服务、技师、时段 | 价格、上门费、可服务校验 | 到家服务-01 |
| API-HOME-006 | `POST /home/orders` | quoteId、会员、地址、支付选择 | 到家订单 id | 到家服务-01 |
| API-HOME-007 | `GET /home/orders/{id}` | 订单 id | 订单与服务状态 | 到家服务-01 |
| API-HOME-008 | `POST /home/orders/{id}/cancel` | 原因 | 取消及退款状态 | 到家服务-01 |
| API-HOME-009 | `GET /store/home-orders` | 门店、状态、日期 | 店长订单分页 | 到家服务-02 |
| API-HOME-010 | `POST /store/home-orders/{id}/accept` | 无 | 已接单 | 到家服务-02 |
| API-HOME-011 | `POST /store/home-orders/{id}/dispatch` | employeeId、预计到达 | 派单结果 | 到家服务-02 |
| API-HOME-012 | `POST /store/home-orders/{id}/status` | EN_ROUTE/ARRIVED/SERVING/COMPLETED | 同步后的状态 | 到家服务-02 |

## 9. 报表、数据中心与工作台

报表统一接收 `startAt/endAt/storeIds/employeeIds/groupBy`，列表查询返回表格和汇总；导出调用 `API-COM-003`，不为每张报表重复定义导出接口。报表金额必须能钻取到原始账单、支付、资产或提成流水。

| API 编号 | GET 路径 | 报表 | 需求来源 |
| --- | --- | --- | --- |
| API-RPT-001 | `/reports/employee-service-items` | 员工服务项目 | 统计分析-01 |
| API-RPT-002 | `/reports/employee-turnover` | 员工服务/销售流水 | 统计分析-02 |
| API-RPT-003 | `/reports/member-spending-ranking` | 消费排序 | 统计分析-03 |
| API-RPT-004 | `/reports/card-consumption` | 卡类被消费 | 统计分析-04 |
| API-RPT-005 | `/reports/product-consumption` | 产品被消费 | 统计分析-05 |
| API-RPT-006 | `/reports/service-consumption` | 服务项目被消费 | 统计分析-06 |
| API-RPT-007 | `/reports/member-consumption` | 会员消费 | 统计分析-07 |
| API-RPT-008 | `/reports/employee-product-sales` | 员工销售产品 | 统计分析-08 |
| API-RPT-009 | `/reports/payroll-calculation` | 员工工资计算 | 统计分析-09 |
| API-RPT-010 | `/reports/member-clearing` | 会员清零 | 统计分析-10 |
| API-RPT-011 | `/reports/card-opening-rate` | 办卡率 | 统计分析-11 |
| API-RPT-012 | `/reports/period-revenue` | 阶段营业额 | 统计分析-12 |
| API-RPT-013 | `/reports/comparison` | 门店/员工/时段比较 | 统计分析-13 |
| API-RPT-014 | `/reports/employee-service` | 员工服务 | 统计分析-14 |
| API-RPT-015 | `/reports/daily-revenue` | 当日营业额 | 统计分析-15 |
| API-RPT-016 | `/reports/inactive-members` | 阶段未消费会员 | 统计分析-16 |
| API-RPT-017 | `/reports/member-frequency` | 会员消费频次 | 统计分析-17 |
| API-RPT-018 | `/reports/monthly-traffic` | 月客流 | 统计分析-18 |
| API-RPT-019 | `/reports/period-traffic` | 阶段客流 | 统计分析-19 |
| API-RPT-020 | `/reports/membership-conversion` | 入会率 | 统计分析-20 |
| API-RPT-021 | `/reports/store-business` | 店面营业 | 统计分析-21 |
| API-RPT-022 | `/reports/member-birthdays` | 会员生日 | 统计分析-22 |
| API-RPT-023 | `/reports/home-orders` | 上门服务订单 | 统计分析-23 |
| API-RPT-024 | `/reports/home-service-business` | 上门服务经营 | 统计分析-24 |
| API-RPT-025 | `/reports/store-revenue` | 店铺营业额 | 统计分析-25 |
| API-RPT-026 | `/reports/data-reconciliation` | 账单、库存和统计盘点 | 统计分析-26 |
| API-RPT-027 | `/reports/payslips` | 员工工资单 | 统计分析-27 |
| API-RPT-028 | `/reports/store-consumption` | 店铺消费 | 统计分析-28 |
| API-RPT-029 | `/analytics/daily-overview` | 每日营业速览 | 优化统计-01 |
| API-RPT-030 | `/analytics/financial-reconciliation` | 财务对账表 | 优化统计-02 |
| API-RPT-031 | `/analytics/member-assets` | 客户资产分析 | 优化统计-03 |
| API-RPT-032 | `/analytics/employees` | 员工分析及提成钻取 | 优化统计-04 |
| API-RPT-033 | `/analytics/operation-dashboard` | 经营看板 | 优化统计-05 |
| API-RPT-034 | `/analytics/store-comparison` | 门店对比与 PK | 优化统计-06 |
| API-RPT-035 | `/analytics/customer-traffic` | 本人/朋友/新老客客流 | 优化统计-07 |
| API-RPT-036 | `/analytics/store-business` | 店面营业数据 | 优化统计-08 |
| API-RPT-037 | `/data-center/customers` | 客户主题分析；字段待确认 | 数据中心-待补01 |
| API-RPT-038 | `/data-center/employees` | 员工主题分析；字段待确认 | 数据中心-待补02 |
| API-RPT-039 | `/data-center/services` | 服务主题分析；字段待确认 | 数据中心-待补03 |
| API-RPT-040 | `/data-center/cards` | 卡类主题分析；字段待确认 | 数据中心-待补04 |
| API-RPT-041 | `/data-center/usage-cards` | 次卡主题分析；字段待确认 | 数据中心-待补05 |
| API-RPT-042 | `/workbench/overview` | 今日概况、待办、最近操作 | 优化系统管理-05 |
| API-RPT-043 | `/workbench/shortcuts`、`PUT /workbench/shortcuts` | 当前用户快捷入口查询/排序 | 快捷入口、优化系统管理-05 |

`API-RPT-037~041` 只冻结路径，不进入开发完成统计。甲方补齐维度、指标、公式、筛选、权限、钻取和样例后，更新本表并生成相应测试用例。

## 10. AI 分析推荐

AI 只读取经过权限控制和脱敏的结构化摘要，不直接读取生产库，也不得自动修改会员资产、账单或提成。调用失败时返回降级结果，不影响开单和结算。

| API 编号 | 方法与路径 | 用途及主要入参 | 主要返回/权限 | 需求来源 |
| --- | --- | --- | --- | --- |
| API-AI-001 | `POST /ai/member-tags/suggest` | memberId、允许的数据维度 | 标签建议、依据、置信度、模型版本 | 优化会员-03 |
| API-AI-002 | `POST /ai/member-analysis` | memberId、分析类型 | 消费/活跃/偏好摘要、风险提示 | AI 合并范围 |
| API-AI-003 | `POST /ai/recommendations` | memberId、场景、候选服务/商品 | 推荐列表、理由、禁忌检查 | AI 合并范围 |
| API-AI-004 | `POST /ai/operation-insights` | 门店、期间、指标范围 | 经营异常和行动建议 | AI 合并范围 |
| API-AI-005 | `GET /ai/tasks/{id}` | taskId | 异步任务状态、结果、失败原因 | AI 合并范围 |
| API-AI-006 | `POST /ai/results/{id}/feedback` | ACCEPTED/REJECTED/EDITED、备注 | 反馈记录 | AI 合并范围 |
| API-AI-007 | `GET/PUT /ai/configuration` | 无/模型、超时、脱敏、限额、开关 | 配置；`ai:config:manage` | AI 合并范围 |
| API-AI-008 | `GET /ai/audit-logs` | 用户、场景、模型、日期、结果 | AI 审计分页，不含密钥 | AI 合并范围 |

## 11. 第三方接口与回调

| API 编号 | 方法与路径 | 用途 | 核心控制 |
| --- | --- | --- | --- |
| API-EXT-001 | `POST /callbacks/payments/{channel}` | 微信/支付宝等支付结果 | 验签、金额核对、幂等、原始报文摘要 |
| API-EXT-002 | `POST /callbacks/refunds/{channel}` | 退款结果 | 验签、退款单匹配、重复回调 |
| API-EXT-003 | `POST /callbacks/meituan/verifications` | 美团核销结果 | 验签、券码、门店和金额核对 |
| API-EXT-004 | `POST /callbacks/sms/status` | 短信提交/送达回执 | IP 白名单、签名、批次与号码匹配 |
| API-EXT-005 | `POST /callbacks/sms/inbound` | 客户上行短信 | 签名、去重、手机号脱敏展示 |
| API-EXT-006 | `POST /integrations/maps/geocode` | 地址转经纬度 | 服务端代理、Key 不下发 |
| API-EXT-007 | `POST /integrations/maps/service-area-check` | 地址与服务范围校验 | 距离、门店、半径、上门费 |
| API-EXT-008 | `GET /integrations/health` | 各通道可用性 | 仅管理员，密钥不可见 |

## 12. 核心 DTO 与验收样例

### 12.1 结算试算

```json
{
  "payments": [
    {"paymentMethodId": 3, "amount": 58.00, "externalReference": "WX..."}
  ],
  "balanceAmount": 100.00,
  "points": 1000,
  "cards": [{"billLineId": 20001, "memberCardId": 30001}]
}
```

响应给出 `receivableAmount/paymentTotal/assetAmount/externalPaymentAmount/changeAmount/differenceAmount`，并在 `assets[]` 保存资产类型、抵扣金额、扣减数量、账单行、次卡余额和资产版本。试算生成10分钟有效 `quoteNo`；正式结算必须使用同一账单版本、资产版本和 `quoteNo`。资产或账单在试算后变化时整单失败，不允许部分扣减。

### 12.2 数据追踪要求

- 账单、充值、资产、提成、库存和退款流水全部保存 `source_type/source_id/source_line_id`，可由报表钻取到源单。
- 调账和冲销不覆盖旧数据，必须生成反向流水与新流水，并以 `correlation_id` 串联。
- API 日志只保存必要摘要；支付、短信和 AI 的完整敏感报文进入加密受控存储并设置保留期。
- Swagger 必须提供成功、权限不足、状态冲突、余额不足、重复请求和第三方失败样例。

## 13. 接口冻结与变更流程

1. 接口开发前，功能编号、页面编号、API 编号和数据库表必须完成四向关联。
2. 接口进入联调后，字段只能向后兼容地增加；删除、改名或改变语义必须建立变更记录。
3. 每次接口变更同步修改本文件、OpenAPI、自动化测试和 `plan/migration/` 记录；涉及表结构时必须新增 Flyway SQL。
4. 数据中心 5 项、AI 分析输出口径、支付/短信具体通道在甲方确认前标记为 `BLOCKED`，不能用假数据通过验收。
5. API 完成标准：后端实现、Swagger 可调用、权限和数据范围有效、异常用例通过、审计可查询、对应页面联调通过。

### 13.1 容易混淆的合同编号映射

两份合同存在同名模块，下面项目使用 V3 前缀区分，但仍保留合同原编号：

| 合同编号 | API | 页面 |
| --- | --- | --- |
| 快捷入口-02 | API-MEM-001、API-MEM-003 | UI-MEM-001、UI-MEM-003 |
| 短信-05 | API-MKT-001、API-MEM-018、API-MEM-019 | UI-MKT-001、UI-MEM-008 |
| 结算管理-02 | API-TRD-009、API-TRD-010 | UI-TRD-005 |
| 会员管理-05（V3 归属调整） | API-MEM-011、API-MEM-012 | UI-MEM-009 |
| 会员管理-06（V3 冻结客户） | API-MEM-004、API-MEM-005 | UI-MEM-010 |
| 预约管理-02（V3 预约时段） | API-APT-002、API-APT-003、API-APT-012、API-APT-013 | UI-APT-001、UI-APT-003 |
| 预约管理-03（V3 状态流转） | API-APT-005~009 | UI-APT-001、UI-APT-002 |
| 预约管理-04（V3 账单联动） | API-APT-006、API-APT-010 | UI-APT-002、UI-TRD-003 |
