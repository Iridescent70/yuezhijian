export interface ApiResponse<T> {
  code: string
  message: string
  data: T
  traceId: string
  serverTime: string
}

export interface StoreSummary {
  id: number
  code: string
  name: string
  level: string
  status: string
}

export interface MenuItem {
  id: number
  code: string
  name: string
  route: string
  icon?: string
  sortNo: number
  permission?: string
  children: MenuItem[]
}

export interface CurrentUser {
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

export interface RoleSummary {
  id: number
  code: string
  name: string
  dataScope: string
  status: string
  permissions: string[]
}

export interface WorkbenchOverview {
  businessDate: string
  appointmentCount: number
  customerTraffic: number
  revenue: number
  pendingTaskCount: number
  shortcuts: Array<{
    code: string
    name: string
    route: string
    permission: string
  }>
}
