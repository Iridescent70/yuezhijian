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

export interface PageResult<T> {
  items: T[]
  page: number
  size: number
  total: number
}

export interface MemberSummary {
  id: number
  memberNo: string
  fullName: string
  maskedMobile: string
  gender: string
  levelName: string
  ownerStoreId: number
  ownerStoreName: string
  availableBalance: number
  availablePoints: number
  cardCount: number
  status: string
  lastVisitAt?: string
}

export interface MemberTag {
  id: number
  code: string
  name: string
  color?: string
  negative: boolean
}

export interface MemberAssets {
  availableBalance: number
  frozenBalance: number
  totalRecharged: number
  availablePoints: number
  lifetimePoints: number
  cardCount: number
}

export interface MemberDetail {
  id: number
  memberNo: string
  membershipCardNo: string
  fullName: string
  nickname?: string
  maskedMobile: string
  gender: string
  birthday?: string
  email?: string
  sourceType: string
  joinStoreId: number
  joinStoreName: string
  ownerStoreId: number
  ownerStoreName: string
  advisorEmployeeId?: number
  levelName: string
  special: boolean
  status: string
  lastVisitAt?: string
  createdAt: string
  assets: MemberAssets
  tags: MemberTag[]
  version: string
}

export interface CreateMemberPayload {
  fullName: string
  nickname?: string
  mobile: string
  gender: string
  birthday?: string
  email?: string
  sourceType: string
  joinStoreId: number
  ownerStoreId?: number
  advisorEmployeeId?: number
  membershipCardNo?: string
}

export interface CreatedMember {
  memberId: number
  memberNo: string
  membershipCardNo: string
}
