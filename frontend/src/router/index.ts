import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

declare module 'vue-router' {
  interface RouteMeta {
    title?: string
    requiresAuth?: boolean
    permission?: string
  }
}

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'login',
    component: () => import('@/views/LoginView.vue'),
    meta: { title: '登录' },
  },
  {
    path: '/app',
    component: () => import('@/layouts/MainLayout.vue'),
    meta: { requiresAuth: true },
    children: [
      { path: '', redirect: '/app/workbench' },
      {
        path: 'workbench',
        name: 'workbench',
        component: () => import('@/views/WorkbenchView.vue'),
        meta: { title: '工作台', permission: 'workbench:view' },
      },
      {
        path: 'system/stores',
        name: 'stores',
        component: () => import('@/views/system/StoreListView.vue'),
        meta: { title: '组织门店', permission: 'org:store:view' },
      },
      {
        path: 'system/roles',
        name: 'roles',
        component: () => import('@/views/system/RoleListView.vue'),
        meta: { title: '角色管理', permission: 'iam:role:view' },
      },
      {
        path: 'system/employees',
        name: 'employees',
        component: () => import('@/views/system/EmployeeListView.vue'),
        meta: { title: '员工管理', permission: 'org:employee:view' },
      },
      {
        path: 'system/positions',
        name: 'positions',
        component: () => import('@/views/system/PositionListView.vue'),
        meta: { title: '职务管理', permission: 'org:position:view' },
      },
      {
        path: 'system/workstations',
        name: 'workstations',
        component: () => import('@/views/system/WorkstationListView.vue'),
        meta: { title: '工位管理', permission: 'org:workstation:view' },
      },
      {
        path: 'system/audit-logs',
        name: 'audit-logs',
        component: () => import('@/views/system/AuditLogView.vue'),
        meta: { title: '操作日志', permission: 'system:audit:view' },
      },
      {
        path: 'catalog/products',
        name: 'products',
        component: () => import('@/views/catalog/ProductListView.vue'),
        meta: { title: '产品管理', permission: 'catalog:product:view' },
      },
      {
        path: 'catalog/services',
        name: 'services',
        component: () => import('@/views/catalog/ServiceListView.vue'),
        meta: { title: '服务项目', permission: 'catalog:service:view' },
      },
      {
        path: 'catalog/units',
        name: 'catalog-master-data',
        component: () => import('@/views/catalog/CatalogMasterDataView.vue'),
        meta: { title: '分类与单位', permission: 'catalog:master:view' },
      },
      {
        path: 'catalog/card-types',
        name: 'card-types',
        component: () => import('@/views/catalog/CardTypeListView.vue'),
        meta: { title: '次卡类型', permission: 'catalog:card:view' },
      },
      {
        path: 'members',
        name: 'members',
        component: () => import('@/views/member/MemberListView.vue'),
        meta: { title: '会员管理', permission: 'member:member:view' },
      },
      {
        path: 'members/new',
        name: 'member-create',
        component: () => import('@/views/member/MemberCreateView.vue'),
        meta: { title: '新建会员', permission: 'member:member:create' },
      },
      {
        path: 'members/ownership',
        name: 'member-ownership',
        component: () => import('@/views/member/OwnershipAdjustmentView.vue'),
        meta: { title: '归属调整', permission: 'member:ownership:view' },
      },
      {
        path: 'members/:memberId(\\d+)',
        name: 'member-detail',
        component: () => import('@/views/member/MemberDetailView.vue'),
        meta: { title: '会员详情', permission: 'member:member:view' },
      },
      {
        path: 'members/:pathMatch(.*)*',
        name: 'members-placeholder',
        component: () => import('@/views/ModulePlaceholderView.vue'),
        props: { moduleName: '会员扩展功能', nextMilestone: '标签、客群、冻结与归属调整' },
        meta: { title: '会员扩展功能', permission: 'member:member:view' },
      },
      {
        path: 'appointments',
        name: 'appointments',
        component: () => import('@/views/appointment/AppointmentListView.vue'),
        meta: { title: '预约管理', permission: 'appointment:appointment:view' },
      },
      {
        path: 'appointments/calendar',
        name: 'appointment-calendar',
        component: () => import('@/views/appointment/AppointmentListView.vue'),
        meta: { title: '预约排期', permission: 'appointment:appointment:view' },
      },
      {
        path: 'appointments/new',
        name: 'appointment-create',
        component: () => import('@/views/appointment/AppointmentCreateView.vue'),
        meta: { title: '新建预约', permission: 'appointment:appointment:create' },
      },
      {
        path: 'bills',
        name: 'bills',
        component: () => import('@/views/trade/BillListView.vue'),
        meta: { title: '账单管理', permission: 'trade:bill:view' },
      },
      {
        path: 'bills/new',
        name: 'bill-create',
        component: () => import('@/views/trade/BillCreateView.vue'),
        meta: { title: '新建账单', permission: 'trade:bill:create' },
      },
      {
        path: 'bills/:billId(\\d+)/settle',
        name: 'bill-settle',
        component: () => import('@/views/trade/BillSettlementView.vue'),
        meta: { title: '收银结算', permission: 'trade:bill:settle' },
      },
      {
        path: 'bills/:billId(\\d+)',
        name: 'bill-detail',
        component: () => import('@/views/trade/BillDetailView.vue'),
        meta: { title: '账单详情', permission: 'trade:bill:view' },
      },
      {
        path: 'settlement/reversals',
        name: 'reversals',
        component: () => import('@/views/trade/ReversalListView.vue'),
        meta: { title: '冲销管理', permission: 'trade:reversal:view' },
      },
      {
        path: 'assets/card-refunds',
        name: 'card-refunds',
        component: () => import('@/views/asset/CardRefundListView.vue'),
        meta: { title: '退卡管理', permission: 'member:card:refund:view' },
      },
      {
        path: 'benefits/vouchers',
        name: 'vouchers',
        component: () => import('@/views/benefit/VoucherListView.vue'),
        meta: { title: '代金券管理', permission: 'benefit:voucher:view' },
      },
      {
        path: 'commission/plans',
        name: 'commission-plans',
        component: () => import('@/views/commission/CommissionPlanView.vue'),
        meta: { title: '提成方案', permission: 'commission:plan:view' },
      },
      {
        path: 'commission/ledgers',
        name: 'commission-ledgers',
        component: () => import('@/views/commission/CommissionLedgerView.vue'),
        meta: { title: '提成流水', permission: 'commission:ledger:view' },
      },
      {
        path: 'commission/simulator',
        name: 'commission-simulator',
        component: () => import('@/views/commission/CommissionSimulatorView.vue'),
        meta: { title: '薪资测算', permission: 'commission:plan:view' },
      },
      {
        path: 'service/visits',
        name: 'visit-tasks',
        component: () => import('@/views/visit/VisitTaskView.vue'),
        meta: { title: '回访管理', permission: 'visit:task:view' },
      },
      {
        path: 'service/feedback',
        name: 'service-feedback',
        component: () => import('@/views/feedback/ServiceFeedbackView.vue'),
        meta: { title: '服务反馈', permission: 'visit:feedback:view' },
      },
      {
        path: 'service/satisfaction-rules',
        name: 'satisfaction-rules',
        component: () => import('@/views/settings/SatisfactionRuleView.vue'),
        meta: { title: '满意度规则', permission: 'visit:satisfaction:view' },
      },
      {
        path: 'system/parameters',
        name: 'system-parameters',
        component: () => import('@/views/settings/SystemParameterView.vue'),
        meta: { title: '系统参数', permission: 'system:parameter:view' },
      },
      {
        path: 'system/downloads',
        name: 'download-center',
        component: () => import('@/views/system/DownloadCenterView.vue'),
        meta: { title: '下载中心', permission: 'system:job:view' },
      },
      {
        path: 'forbidden',
        name: 'forbidden',
        component: () => import('@/views/ForbiddenView.vue'),
        meta: { title: '无权限' },
      },
    ],
  },
  { path: '/', redirect: '/app/workbench' },
  { path: '/:pathMatch(.*)*', redirect: '/app/workbench' },
]

const router = createRouter({ history: createWebHistory(), routes })

router.beforeEach(async (to) => {
  const auth = useAuthStore()
  await auth.initialize()
  if (to.meta.requiresAuth && !auth.isAuthenticated) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }
  if (to.name === 'login' && auth.isAuthenticated) {
    return { name: 'workbench' }
  }
  if (to.meta.permission && !auth.hasPermission(to.meta.permission)) {
    return { name: 'forbidden' }
  }
  document.title = `${to.meta.title ?? '管理系统'} - 悦·指间`
  return true
})

export default router
