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

export interface CreatedResource {
  id: number
}

export interface PositionOption {
  id: number
  code: string
  name: string
  status: string
}

export interface CategoryOption {
  id: number
  code: string
  name: string
  type: string
  status: string
}

export interface EmployeeSummary {
  id: number
  employeeNo: string
  name: string
  maskedMobile?: string
  positionId: number
  positionName: string
  storeId: number
  storeName: string
  canService: boolean
  canSell: boolean
  status: string
}

export interface WorkstationSummary {
  id: number
  storeId: number
  storeName: string
  code: string
  name: string
  capacity: number
  sortNo: number
  status: string
}

export interface ServiceItemSummary {
  id: number
  code: string
  name: string
  categoryId: number
  categoryName: string
  durationMinutes: number
  costAmount: number
  listPrice: number
  storePrice: number
  saleStatus: string
  status: string
}

export interface CreateEmployeePayload {
  employeeNo: string
  name: string
  mobile?: string
  positionId: number
  primaryStoreId: number
  canService: boolean
  canSell: boolean
}

export interface CreateWorkstationPayload {
  storeId: number
  code: string
  name: string
  capacity: number
  sortNo: number
}

export interface CreateServiceItemPayload {
  code: string
  name: string
  categoryId: number
  durationMinutes: number
  costAmount: number
  listPrice: number
  storePrice: number
  storeIds: number[]
  description?: string
}

export type AppointmentStatus =
  | 'PENDING_CONFIRM'
  | 'CONFIRMED'
  | 'ARRIVED'
  | 'SERVING'
  | 'COMPLETED'
  | 'CANCELLED'
  | 'NO_SHOW'

export interface AppointmentSummary {
  id: number
  appointmentNo: string
  memberId?: number
  customerName: string
  maskedMobile?: string
  storeId: number
  storeName: string
  sourceType: string
  appointmentType: string
  startAt: string
  endAt: string
  personCount: number
  employeeId?: number
  employeeName?: string
  workstationId?: number
  workstationName?: string
  serviceNames: string
  note?: string
  status: AppointmentStatus
  version: string
}

export interface AppointmentServiceLine {
  serviceId: number
  serviceName: string
  durationMinutes: number
  price: number
  sortNo: number
}

export interface AppointmentHistoryItem {
  id: number
  fromStatus?: AppointmentStatus
  toStatus: AppointmentStatus
  reasonCode?: string
  note?: string
  occurredAt: string
  operatorId: number
}

export interface AppointmentDetail {
  appointment: AppointmentSummary
  services: AppointmentServiceLine[]
  history: AppointmentHistoryItem[]
}

export interface CreatedAppointment {
  id: number
  appointmentNo: string
  status: AppointmentStatus
  version: string
}

export interface CreateAppointmentPayload {
  memberId?: number
  guestName?: string
  guestMobile?: string
  storeId: number
  sourceType: string
  appointmentType: string
  startAt: string
  personCount: number
  employeeId: number
  workstationId: number
  serviceIds: number[]
  designated: boolean
  note?: string
  idempotencyKey: string
}

export interface UpdateAppointmentPayload {
  startAt: string
  personCount: number
  employeeId: number
  workstationId: number
  serviceIds: number[]
  designated: boolean
  note?: string
  version: string
}

export interface AppointmentTransitionPayload {
  version: string
  reasonCode?: string
  note?: string
  personCount?: number
}

export interface CancelReasonOption {
  code: string
  name: string
  requiresNote: boolean
}

export interface AvailabilitySlot {
  startAt: string
  endAt: string
  available: boolean
  unavailableReason?: string
}

export type BillStatus = 'DRAFT' | 'PENDING_PAYMENT' | 'SETTLED' | 'VOIDED' | 'ADJUSTED' | 'REVERSED'

export interface BillSummary {
  id: number
  billNo: string
  appointmentId?: number
  memberId?: number
  customerName: string
  maskedMobile?: string
  storeId: number
  storeName: string
  sourceType: string
  personCount: number
  originalAmount: number
  discountAmount: number
  receivableAmount: number
  receivedAmount: number
  changeAmount: number
  status: BillStatus
  note?: string
  settledAt?: string
  createdAt: string
  version: string
}

export interface BillLine {
  id: number
  lineNo: number
  itemType: string
  itemId: number
  itemCode: string
  itemName: string
  unitPrice: number
  quantity: number
  originalAmount: number
  discountAmount: number
  receivableAmount: number
  actualAmount: number
  employeeId?: number
  employeeName?: string
  note?: string
}

export interface BillPayment {
  id: number
  paymentNo: string
  paymentMethodId: number
  paymentMethodName: string
  amount: number
  status: string
  externalReference?: string
  paidAt: string
}

export interface BillHistoryItem {
  id: number
  fromStatus?: BillStatus
  toStatus: BillStatus
  reasonCode?: string
  note?: string
  occurredAt: string
  operatorId: number
}

export interface BillDetail {
  bill: BillSummary
  lines: BillLine[]
  payments: BillPayment[]
  history: BillHistoryItem[]
}

export interface CreatedBill {
  id: number
  billNo: string
  status: BillStatus
  version: string
}

export interface PaymentMethodOption {
  id: number
  code: string
  name: string
  type: string
  electronic: boolean
  includedInRevenue: boolean
  needsExternalReference: boolean
  sortNo: number
}

export interface QuotePayment {
  paymentMethodId: number
  paymentMethodCode: string
  paymentMethodName: string
  amount: number
  externalReference?: string
}

export interface SettlementQuote {
  quoteNo: string
  billId: number
  billVersion: string
  receivableAmount: number
  paymentTotal: number
  changeAmount: number
  differenceAmount: number
  payments: QuotePayment[]
  expiresAt: string
  used: boolean
}
