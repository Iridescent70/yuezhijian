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
        path: 'members/:pathMatch(.*)*',
        name: 'members-placeholder',
        component: () => import('@/views/ModulePlaceholderView.vue'),
        props: { moduleName: '会员管理', nextMilestone: 'T2 会员建档与资产聚合' },
        meta: { title: '会员管理', permission: 'member:member:view' },
      },
      {
        path: 'appointments/:pathMatch(.*)*',
        name: 'appointments-placeholder',
        component: () => import('@/views/ModulePlaceholderView.vue'),
        props: { moduleName: '预约管理', nextMilestone: 'T2 预约排期与到店状态' },
        meta: { title: '预约管理', permission: 'appointment:appointment:view' },
      },
      {
        path: 'bills/:pathMatch(.*)*',
        name: 'bills-placeholder',
        component: () => import('@/views/ModulePlaceholderView.vue'),
        props: { moduleName: '账单管理', nextMilestone: 'T2 开单与混合支付结算' },
        meta: { title: '账单管理', permission: 'trade:bill:view' },
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
