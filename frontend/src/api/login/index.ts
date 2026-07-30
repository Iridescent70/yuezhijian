import request from '@/config/axios'
import { resetCsrfToken } from '@/config/axios/csrf'
import type { RegisterVO, TokenType, UserLoginVO } from './types'

interface StoreSummary {
  id: number
  code: string
  name: string
  level: string
  status: string
}

interface MenuItem {
  id: number
  code: string
  name: string
  route: string
  icon?: string
  permission?: string
  children: MenuItem[]
}

interface CurrentUser {
  id: number
  username: string
  fullName: string
  currentStoreId: number
  currentStoreName: string
  roles: string[]
  permissions: string[]
  stores: StoreSummary[]
  menus: MenuItem[]
}

const toIconifyName = (icon?: string) => {
  if (!icon) return 'ep:menu'
  if (icon.includes(':')) return icon
  return `ep:${icon.replace(/([a-z0-9])([A-Z])/g, '$1-$2').toLowerCase()}`
}

const commonMenuPath = (menus: MenuItem[]) => {
  const paths = menus.map((menu) => menu.route.split('/').filter(Boolean))
  const common: string[] = []
  for (let index = 0; index < (paths[0]?.length || 0); index += 1) {
    const segment = paths[0][index]
    if (!paths.every((path) => path[index] === segment)) break
    common.push(segment)
  }
  return common.length ? `/${common.join('/')}` : ''
}

const childPath = (fullPath: string, parentPath?: string) => {
  if (!parentPath) return fullPath
  if (fullPath === parentPath) return ''
  if (fullPath.startsWith(`${parentPath}/`)) return fullPath.slice(parentPath.length + 1)
  return fullPath.replace(/^\//, '')
}

const menuComponent = (menu: MenuItem) =>
  menu.code === 'workbench' ? '/Home/Index' : '/yuezhijian/migration/PendingView'

const mapMenu = (menu: MenuItem, parentId = 0, parentPath?: string): AppCustomRouteRecordRaw => {
  const fullPath = menu.children.length ? commonMenuPath(menu.children) || menu.route : menu.route
  return {
    id: menu.id,
    parentId,
    path: childPath(fullPath, parentPath),
    name: menu.name,
    component: menu.children.length ? '' : menuComponent(menu),
    componentName: `YuezhijianMenu${menu.id}`,
    redirect: '',
    icon: toIconifyName(menu.icon),
    visible: true,
    keepAlive: true,
    alwaysShow: menu.children.length > 1,
    meta: {},
    children: menu.children.map((child) => mapMenu(child, menu.id, fullPath))
  }
}

const mapCurrentUser = (user: CurrentUser) => ({
  permissions: user.permissions,
  roles: user.roles,
  user: {
    id: user.id,
    username: user.username,
    nickname: user.fullName,
    deptId: user.currentStoreId,
    avatar: '',
    currentStoreId: user.currentStoreId,
    currentStoreName: user.currentStoreName,
    stores: user.stores
  },
  menus: user.menus.map((menu) => mapMenu(menu))
})

export interface SmsCodeVO {
  mobile: string
  scene: number
}

export interface SmsLoginVO {
  mobile: string
  code: string
}

// 登录
export const login = async (data: UserLoginVO): Promise<TokenType> => {
  const user = await request.post<CurrentUser>({
    url: '/auth/login',
    data: {
      username: data.username,
      password: data.password
    },
    headers: {
      isEncrypt: false
    }
  })
  return {
    id: user.id,
    accessToken: 'spring-session',
    refreshToken: 'spring-session',
    userId: user.id,
    userType: 2,
    clientId: 'yuezhijian-admin',
    expiresTime: Date.now() + 8 * 60 * 60 * 1000
  }
}

// 获取当前登录用户、权限和动态菜单
export const getInfo = async () => {
  const user = await request.get<CurrentUser>({ url: '/auth/me' })
  return mapCurrentUser(user)
}

// 切换当前会话门店，返回切换后的用户、权限和菜单快照。
export const switchCurrentStore = async (storeId: number) => {
  const user = await request.post<CurrentUser>({
    url: '/auth/current-store',
    data: { storeId }
  })
  return mapCurrentUser(user)
}

// 登出
export const loginOut = async () => {
  try {
    await request.post({ url: '/auth/logout' })
  } finally {
    resetCsrfToken()
  }
}

// 注册仍保留上游接口定义，当前项目未开放入口。
export const register = (data: RegisterVO) => {
  return request.post({
    url: '/system/auth/register',
    data
  })
}

// 使用租户名，获得租户编号
export const getTenantIdByName = (name: string) => {
  return request.get({ url: '/system/tenant/get-id-by-name?name=' + name })
}

// 使用租户域名，获得租户信息
export const getTenantByWebsite = (website: string) => {
  return request.get({ url: '/system/tenant/get-by-website?website=' + website })
}

//获取登录验证码
export const sendSmsCode = (data: SmsCodeVO) => {
  return request.post({ url: '/system/auth/send-sms-code', data })
}

// 短信验证码登录
export const smsLogin = (data: SmsLoginVO) => {
  return request.post({ url: '/system/auth/sms-login', data })
}

// 社交快捷登录，使用 code 授权码
export function socialLogin(type: string, code: string, state: string) {
  return request.post({
    url: '/system/auth/social-login',
    data: {
      type,
      code,
      state
    }
  })
}

// 社交授权的跳转
export const socialAuthRedirect = (type: number, redirectUri: string) => {
  return request.get({
    url: '/system/auth/social-auth-redirect?type=' + type + '&redirectUri=' + redirectUri
  })
}
// 获取验证图片以及 token
export const getCode = (data: any) => {
  return request.postOriginal({ url: 'system/captcha/get', data })
}

// 滑动或者点选验证
export const reqCheck = (data: any) => {
  return request.postOriginal({ url: 'system/captcha/check', data })
}

// 通过短信重置密码
export const smsResetPassword = (data: any) => {
  return request.post({ url: '/system/auth/reset-password', data })
}
