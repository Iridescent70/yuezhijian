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

export interface OperationChange {
  field: string
  label: string
  beforeValue?: string
  afterValue?: string
}

export interface OperationHistoryItem {
  id: number
  action: string
  actionLabel: string
  operatorId: number
  operatorName: string
  storeId?: number
  occurredAt: string
  traceId: string
  changes: OperationChange[]
}

export interface AuditLogSummary {
  id: number
  traceId: string
  userId?: number
  operatorName: string
  storeId?: number
  module: string
  action: string
  objectType?: string
  objectId?: string
  result: 'SUCCESS' | 'FAILURE'
  errorCode?: string
  occurredAt: string
}

export interface AuditLogDetail extends AuditLogSummary {
  ip?: string
  beforeValues: Record<string, string | undefined>
  afterValues: Record<string, string | undefined>
}

export interface ServiceArea {
  id: number
  storeId: number
  storeCode: string
  storeName: string
  city: string
  district: string
  address: string
  longitude: number
  latitude: number
  radiusKm: number
  visitFee: number
  status: 'ACTIVE' | 'DISABLED'
  updatedAt: string
  updatedBy?: number
  updatedByName: string
  version: string
}

export interface CreateServiceAreaPayload {
  storeId: number
  city: string
  district: string
  address: string
  longitude: number
  latitude: number
  radiusKm: number
  visitFee: number
}

export interface UpdateServiceAreaPayload extends Omit<CreateServiceAreaPayload, 'storeId'> {
  status: 'ACTIVE' | 'DISABLED'
  version: string
}

export type AsyncJobStatus = 'PENDING' | 'RUNNING' | 'SUCCEEDED' | 'PARTIAL' | 'FAILED' | 'CANCELLED'

export interface AsyncJobItem {
  id: number
  jobNo: string
  jobName: string
  jobType: string
  status: AsyncJobStatus
  progress: number
  successCount: number
  failureCount: number
  resultFileId?: number
  resultFileName?: string
  errorFileId?: number
  errorFileName?: string
  errorMessage?: string
  startedAt?: string
  finishedAt?: string
  expiresAt: string
  createdAt: string
  createdBy: number
  createdByName: string
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
  frozenAt?: string
  freezeReason?: string
  lastVisitAt?: string
  createdAt: string
  assets: MemberAssets
  tags: MemberTag[]
  version: string
}

export interface UpdateMemberPayload {
  fullName: string
  nickname?: string
  mobile?: string
  gender: string
  birthday?: string
  email?: string
  advisorEmployeeId?: number
  special: boolean
  version: string
}

export interface ChangeMemberStatusPayload {
  status: 'ACTIVE' | 'FROZEN' | 'INACTIVE'
  reason: string
  version: string
}

export interface MemberTagOption {
  id: number
  code: string
  name: string
  source: 'MANUAL' | 'RULE' | 'AI'
  color?: string
  negative: boolean
}

export interface UpdateMemberTagsPayload {
  addIds: number[]
  removeIds: number[]
  version: string
}

export interface MemberBatchItemResult {
  memberId: number
  memberNo?: string
  memberName?: string
  status: 'SUCCESS' | 'SKIPPED' | 'FAILED'
  message: string
}

export interface MemberBatchResult {
  operation: 'FREEZE' | 'UPDATE_TAGS' | 'ASSIGN_ADVISOR'
  total: number
  succeeded: number
  skipped: number
  failed: number
  items: MemberBatchItemResult[]
}

export interface BatchFreezeMembersPayload {
  memberIds: number[]
  reason: string
}

export interface BatchUpdateMemberTagsPayload {
  memberIds: number[]
  addIds: number[]
  removeIds: number[]
}

export interface BatchAssignMemberAdvisorPayload {
  memberIds: number[]
  employeeId: number
}

export interface OwnershipAdjustment {
  id: number
  adjustmentNo: string
  memberId: number
  memberNo: string
  memberName: string
  oldStoreId: number
  oldStoreName: string
  newStoreId: number
  newStoreName: string
  effectiveDate: string
  shareRule: Record<string, unknown>
  reason: string
  approvalStatus: 'PENDING' | 'APPROVED' | 'REJECTED'
  executionStatus: 'WAITING' | 'PROCESSING' | 'APPLIED' | 'FAILED' | 'CANCELLED'
  requestedBy: number
  requestedAt: string
  reviewedBy?: number
  reviewedAt?: string
  reviewComment?: string
  appliedAt?: string
  executionMessage?: string
  version: string
}

export interface CreateOwnershipAdjustmentPayload {
  newStoreId: number
  effectiveDate: string
  shareRule: Record<string, unknown>
  reason: string
  memberVersion: string
}

export interface ReviewOwnershipAdjustmentPayload {
  comment?: string
  version: string
}

export interface BalanceAccount {
  memberId: number
  availableBalance: number
  frozenBalance: number
  totalRecharged: number
  lastTransactionAt?: string
  version: string
}

export interface PointAccount {
  memberId: number
  availablePoints: number
  lifetimePoints: number
  lastTransactionAt?: string
  version: string
}

export interface BalanceLedgerItem {
  id: number
  memberId: number
  ledgerNo: string
  transactionType: string
  beforeBalance: number
  changeAmount: number
  afterBalance: number
  sourceType: string
  sourceId: number
  storeId: number
  storeName: string
  occurredAt: string
  correlationId: string
  reversedLedgerId?: number
  note?: string
}

export interface PointLedgerItem {
  id: number
  memberId: number
  ledgerNo: string
  transactionType: string
  beforePoints: number
  changePoints: number
  afterPoints: number
  sourceType: string
  sourceId: number
  expiredAt?: string
  occurredAt: string
  correlationId: string
  reversedLedgerId?: number
  note?: string
}

export interface RechargeQuote {
  id: number
  quoteNo: string
  memberId: number
  rechargeAmount: number
  giftAmount: number
  creditAmount: number
  paymentMethodId: number
  paymentMethodName: string
  expiresAt: string
  used: boolean
}

export interface RechargeOrder {
  id: number
  rechargeNo: string
  quoteNo: string
  memberId: number
  storeId: number
  storeName: string
  rechargeAmount: number
  giftAmount: number
  creditAmount: number
  paymentMethodId: number
  paymentMethodName: string
  externalReference?: string
  salesEmployeeId?: number
  status: 'PENDING_CONFIRM' | 'CONFIRMED' | 'CANCELLED'
  confirmedAt?: string
  cancelledAt?: string
  cancelReason?: string
  createdAt: string
  version: string
}

export interface CardServiceRule {
  serviceId: number
  serviceCode: string
  serviceName: string
  includedTimes: number
  deductTimes: number
  priority: number
}

export interface CardTypeDetail {
  id: number
  code: string
  name: string
  salePrice: number
  listPrice: number
  totalTimes: number
  validDays: number
  purchaseThreshold: number
  instructions?: string
  autoRemindDays: number
  storeIds: number[]
  serviceRules: CardServiceRule[]
  status: string
  version: string
}

export interface CreateCardTypePayload {
  code: string
  name: string
  salePrice: number
  listPrice: number
  totalTimes: number
  validDays: number
  purchaseThreshold: number
  instructions?: string
  autoRemindDays: number
  storeIds: number[]
  serviceRules: Array<{
    serviceId: number
    includedTimes: number
    deductTimes: number
    priority: number
  }>
}

export interface MemberCardSummary {
  id: number
  cardNo: string
  memberId: number
  cardTypeId: number
  cardTypeCode: string
  cardTypeName: string
  purchaseStoreId: number
  purchaseStoreName: string
  purchasePrice: number
  totalTimes: number
  remainingTimes: number
  frozenTimes: number
  startedAt: string
  expiresAt: string
  status: string
  version: string
}

export interface MemberCardBalanceItem {
  id: number
  serviceId: number
  serviceCode: string
  serviceName: string
  totalTimes: number
  remainingTimes: number
  frozenTimes: number
  deductTimes: number
  version: string
}

export interface MemberCardLedgerItem {
  id: number
  ledgerNo: string
  serviceId: number
  serviceName: string
  transactionType: string
  beforeTimes: number
  changeTimes: number
  afterTimes: number
  valueAmount: number
  sourceType: string
  sourceId: number
  occurredAt: string
  correlationId: string
  reversedLedgerId?: number
  note?: string
}

export interface MemberCardDetail {
  card: MemberCardSummary
  balances: MemberCardBalanceItem[]
  ledgers: MemberCardLedgerItem[]
}

export interface CardSaleResult {
  orderId: number
  orderNo: string
  totalAmount: number
  cards: MemberCardSummary[]
}

export interface CardExchangeQuote {
  id: number
  quoteNo: string
  oldCardId: number
  oldCardNo: string
  oldCardTypeName: string
  targetCardTypeId: number
  targetCardTypeName: string
  targetCardTypeVersion: string
  oldRemainingTimes: number
  oldRemainingValue: number
  newCardValue: number
  differenceAmount: number
  oldCardVersion: string
  expiresAt: string
  used: boolean
}

export interface CardExchangePayment {
  paymentMethodId: number
  paymentMethodName: string
  amount: number
  externalReference?: string
}

export interface CardExchangeResult {
  exchangeId: number
  exchangeNo: string
  oldCard: MemberCardSummary
  newCard: MemberCardSummary
  oldRemainingValue: number
  newCardValue: number
  differenceAmount: number
  payments: CardExchangePayment[]
  executedAt: string
}

export interface CardTransferResult {
  transferId: number
  transferNo: string
  sourceCard: MemberCardSummary
  targetCard: MemberCardSummary
  sourceMemberId: number
  recipientMemberId: number
  recipientMemberName: string
  remainingTimes: number
  remainingValue: number
  oldExpiresAt: string
  newExpiresAt: string
  reason: string
  executedAt: string
}

export interface CardConsumptionRepriceItem {
  cardLedgerId: number
  billId: number
  billNo: string
  serviceId: number
  serviceName: string
  consumedAt: string
  originalAmount: number
}

export interface CardRefundQuote {
  id: number
  quoteNo: string
  memberCardId: number
  cardNo: string
  cardTypeName: string
  memberId: number
  originalAmount: number
  consumedRepriceAmount: number
  feeAmount: number
  refundAmount: number
  cardVersion: string
  items: CardConsumptionRepriceItem[]
  expiresAt: string
  used: boolean
}

export type CardRefundStatus = 'SUBMITTED' | 'APPROVED' | 'REJECTED' | 'EXECUTED'

export interface CardRefundRequestSummary {
  id: number
  quoteId: number
  requestNo: string
  memberCardId: number
  cardNo: string
  cardTypeName: string
  memberId: number
  memberName: string
  storeName: string
  originalAmount: number
  consumedRepriceAmount: number
  feeAmount: number
  refundAmount: number
  refundMethodId?: number
  refundMethodName?: string
  refundMethodRequiresReference: boolean
  status: CardRefundStatus
  commissionAdjustmentStatus: string
  reason: string
  requestedAt: string
  requestedBy: number
  reviewedAt?: string
  reviewedBy?: number
  reviewComment?: string
  executedAt?: string
  cardVersion: string
  version: string
}

export interface CardRefundPayment {
  paymentMethodId: number
  paymentMethodName: string
  amount: number
  status: string
  externalRefundReference?: string
  completedAt?: string
}

export interface CardRefundRequestDetail {
  request: CardRefundRequestSummary
  consumedItems: CardConsumptionRepriceItem[]
  payment?: CardRefundPayment
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
  level: number
  defaultServiceRate: number
  defaultSalesRate: number
  status: string
  version: string
}

export interface CreatePositionPayload {
  code: string
  name: string
  level: number
  defaultServiceRate: number
  defaultSalesRate: number
}

export interface UpdatePositionPayload {
  name: string
  level: number
  defaultServiceRate: number
  defaultSalesRate: number
  status: string
  version: string
}

export interface CategoryOption {
  id: number
  code: string
  name: string
  type: string
  sortNo: number
  status: string
  version: string
}

export interface CreateCategoryPayload {
  type: 'PRODUCT' | 'SERVICE'
  code: string
  name: string
  sortNo: number
}

export interface UpdateCategoryPayload {
  name: string
  sortNo: number
  status: string
  version: string
}

export interface UnitOption {
  id: number
  code: string
  name: string
  decimalPlaces: number
  status: string
  version: string
}

export interface CreateUnitPayload {
  code: string
  name: string
  decimalPlaces: number
}

export interface UpdateUnitPayload {
  name: string
  decimalPlaces: number
  status: string
  version: string
}

export interface ProductStoreConfig {
  storeId: number
  storeName: string
  storePrice: number
  saleStatus: string
}

export interface ProductSummary {
  id: number
  code: string
  name: string
  categoryId: number
  categoryName: string
  unitId: number
  unitName: string
  barcode?: string
  costPrice: number
  salePrice: number
  storePrice: number
  trackStock: boolean
  saleStatus: string
  status: string
}

export interface ProductDetail {
  id: number
  code: string
  name: string
  categoryId: number
  categoryName: string
  unitId: number
  unitName: string
  barcode?: string
  costPrice: number
  salePrice: number
  trackStock: boolean
  description?: string
  status: string
  stores: ProductStoreConfig[]
  version: string
}

export interface ProductBatchItemResult {
  productId: number
  productCode?: string
  productName?: string
  status: 'SUCCESS' | 'SKIPPED' | 'FAILED'
  message: string
}

export interface ProductBatchResult {
  operation: 'BATCH_SALE_STATUS'
  total: number
  succeeded: number
  skipped: number
  failed: number
  items: ProductBatchItemResult[]
}

export interface CreateProductPayload {
  code: string
  name: string
  categoryId: number
  unitId: number
  barcode?: string
  costPrice: number
  salePrice: number
  storePrice: number
  trackStock: boolean
  storeIds: number[]
  description?: string
}

export interface UpdateProductPayload {
  name: string
  categoryId: number
  unitId: number
  barcode?: string
  costPrice: number
  salePrice: number
  trackStock: boolean
  description?: string
  status: string
  storeId: number
  storePrice: number
  saleStatus: string
  version: string
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
  hireDate?: string
  leaveDate?: string
  canService: boolean
  canSell: boolean
  status: string
  version: string
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
  version: string
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

export interface ServiceStoreConfig {
  storeId: number
  storeName: string
  storePrice: number
  saleStatus: string
}

export interface ServiceItemDetail {
  id: number
  code: string
  name: string
  categoryId: number
  categoryName: string
  durationMinutes: number
  costAmount: number
  listPrice: number
  description?: string
  status: string
  stores: ServiceStoreConfig[]
  version: string
}

export interface CreateEmployeePayload {
  employeeNo: string
  name: string
  mobile?: string
  positionId: number
  primaryStoreId: number
  hireDate?: string
  canService: boolean
  canSell: boolean
}

export interface UpdateEmployeePayload {
  name: string
  mobile?: string
  positionId: number
  primaryStoreId: number
  hireDate?: string
  leaveDate?: string
  canService: boolean
  canSell: boolean
  status: string
  version: string
}

export interface CreateWorkstationPayload {
  storeId: number
  code: string
  name: string
  capacity: number
  sortNo: number
}

export interface UpdateWorkstationPayload {
  name: string
  capacity: number
  sortNo: number
  status: string
  version: string
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

export interface UpdateServiceItemPayload {
  name: string
  categoryId: number
  durationMinutes: number
  costAmount: number
  listPrice: number
  storeId: number
  storePrice: number
  saleStatus: string
  status: string
  description?: string
  version: string
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

export type CancelReasonBusinessType = 'APPOINTMENT' | 'BILL' | 'HOME_SERVICE'

export interface CancelReason {
  id: number
  businessType: CancelReasonBusinessType
  code: string
  name: string
  requiresNote: boolean
  sortNo: number
  status: 'ACTIVE' | 'DISABLED'
  updatedAt: string
  updatedBy?: number
  updatedByName: string
  version: string
}

export interface CreateCancelReasonPayload {
  businessType: CancelReasonBusinessType
  code: string
  name: string
  requiresNote: boolean
  sortNo: number
}

export interface UpdateCancelReasonPayload {
  name: string
  requiresNote: boolean
  sortNo: number
  status: 'ACTIVE' | 'DISABLED'
  version: string
}

export type BannerPositionCode = 'PC_HOME' | 'HOME_SERVICE_HOME'
export type BannerLinkType = 'NONE' | 'INTERNAL' | 'EXTERNAL'

export interface Banner {
  id: number
  positionCode: BannerPositionCode
  title: string
  imageFileId: number
  imageName: string
  imageContentType: string
  linkType: BannerLinkType
  linkValue?: string
  sortNo: number
  validFrom?: string
  validTo?: string
  status: 'ACTIVE' | 'DISABLED'
  updatedAt: string
  updatedBy?: number
  updatedByName: string
  version: string
}

export interface ActiveBanner {
  id: number
  title: string
  linkType: BannerLinkType
  linkValue?: string
  sortNo: number
  version: string
}

export interface CreateBannerPayload {
  positionCode: BannerPositionCode
  title: string
  linkType: BannerLinkType
  linkValue?: string
  sortNo: number
  validFrom?: string
  validTo?: string
}

export interface UpdateBannerPayload extends CreateBannerPayload {
  status: 'ACTIVE' | 'DISABLED'
  version: string
}

export interface ColorStyleCategory {
  id: number
  parentId?: number
  code: string
  name: string
  imageFileId?: number
  imageName?: string
  imageContentType?: string
  sortNo: number
  status: 'ACTIVE' | 'DISABLED'
  updatedAt: string
  updatedBy?: number
  updatedByName: string
  version: string
}

export interface ColorStyleAsset {
  id: number
  colorStyleId: number
  fileId: number
  fileName: string
  contentType: string
  sortNo: number
  status: 'ACTIVE' | 'DISABLED'
  updatedAt: string
  version: string
}

export interface ColorStyle {
  id: number
  code: string
  name: string
  description?: string
  sortNo: number
  status: 'ACTIVE' | 'DISABLED'
  updatedAt: string
  updatedBy?: number
  updatedByName: string
  version: string
  categoryIds: number[]
  assets: ColorStyleAsset[]
}

export interface CreateColorStyleCategoryPayload {
  parentId?: number
  code: string
  name: string
  sortNo: number
}

export interface UpdateColorStyleCategoryPayload {
  parentId?: number
  name: string
  sortNo: number
  status: 'ACTIVE' | 'DISABLED'
  version: string
}

export interface CreateColorStylePayload {
  code: string
  name: string
  description?: string
  sortNo: number
  categoryIds: number[]
}

export interface UpdateColorStylePayload {
  name: string
  description?: string
  sortNo: number
  status: 'ACTIVE' | 'DISABLED'
  categoryIds: number[]
  version: string
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

export interface BillDiscountItem {
  id: number
  batchNo: string
  billLineId: number
  discountType: 'AMOUNT' | 'RATE'
  originalAmount: number
  discountAmount: number
  reason: string
  authorizationUserId: number
  createdAt: string
}

export interface BillAssetUsageItem {
  id: number
  assetType: 'BALANCE' | 'POINT' | 'CARD'
  memberId: number
  memberCardId?: number
  memberCardBalanceId?: number
  billLineId?: number
  serviceId?: number
  quantity: number
  amount: number
  assetLedgerId?: number
  displayName: string
  createdAt: string
}

export interface BillDetail {
  bill: BillSummary
  lines: BillLine[]
  payments: BillPayment[]
  discounts: BillDiscountItem[]
  assetUsages: BillAssetUsageItem[]
  history: BillHistoryItem[]
}

export type ReversalStatus = 'SUBMITTED' | 'APPROVED' | 'REJECTED' | 'EXECUTED'

export interface ReversalSummary {
  id: number
  reversalNo: string
  billId: number
  billNo: string
  customerName: string
  storeName: string
  refundAmount: number
  status: ReversalStatus
  reason: string
  requestedAt: string
  requestedBy: number
  reviewedAt?: string
  reviewedBy?: number
  reviewComment?: string
  executedAt?: string
  version: string
}

export interface ReversalPaymentImpact {
  paymentId: number
  paymentMethodName: string
  amount: number
  status: string
}

export interface ReversalAssetImpact {
  usageId: number
  assetType: 'BALANCE' | 'POINT' | 'CARD'
  memberId: number
  memberCardId?: number
  memberCardBalanceId?: number
  billLineId?: number
  serviceId?: number
  quantity: number
  amount: number
  assetLedgerId: number
  displayName: string
}

export interface ReversalDetail {
  reversal: ReversalSummary
  payments: ReversalPaymentImpact[]
  assets: ReversalAssetImpact[]
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

export type PaymentMethodType = 'CASH' | 'BANK_CARD' | 'WECHAT' | 'ALIPAY' | 'MEITUAN' | 'STORED_VALUE' | 'OTHER'

export interface PaymentMethodStoreConfiguration {
  storeId: number
  storeCode: string
  storeName: string
  applicable: boolean
  enabled: boolean
  sortNo: number
  version?: string
}

export interface PaymentMethodConfiguration {
  id: number
  code: string
  name: string
  type: PaymentMethodType
  electronic: boolean
  includedInRevenue: boolean
  needsExternalReference: boolean
  status: 'ACTIVE' | 'DISABLED'
  updatedAt: string
  version: string
  stores: PaymentMethodStoreConfiguration[]
}

export interface CreatePaymentMethodPayload {
  code: string
  name: string
  type: PaymentMethodType
  electronic: boolean
  includedInRevenue: boolean
  needsExternalReference: boolean
  status: 'ACTIVE' | 'DISABLED'
  storeIds: number[]
}

export interface UpdatePaymentMethodPayload extends Omit<CreatePaymentMethodPayload, 'code' | 'storeIds'> {
  version: string
}

export interface QuotePayment {
  paymentMethodId: number
  paymentMethodCode: string
  paymentMethodName: string
  amount: number
  externalReference?: string
}

export interface SettlementAssetUsage {
  assetType: 'BALANCE' | 'POINT' | 'CARD' | 'VOUCHER'
  memberId: number
  voucherCodeId?: number
  memberCardId?: number
  memberCardBalanceId?: number
  billLineId?: number
  serviceId?: number
  quantity: number
  amount: number
  assetVersion: string
  displayName: string
}

export interface CardSettlementOption {
  billLineId: number
  billLineName: string
  memberCardId: number
  cardNo: string
  cardTypeName: string
  memberCardBalanceId: number
  remainingTimes: number
  deductTimes: number
  requiredTimes: number
  expiresAt: string
  recommended: boolean
}

export interface SettlementAssetOptions {
  balanceAccount?: BalanceAccount
  pointAccount?: PointAccount
  pointsPerYuan: number
  cardOptions: CardSettlementOption[]
  voucherOptions: VoucherSettlementOption[]
}

export interface VoucherDefinition {
  id: number
  code: string
  name: string
  benefitType: 'FIXED_AMOUNT' | 'DISCOUNT'
  faceAmount: number
  discountRate: number
  minSpend: number
  validDays: number
  commissionRule?: string
  status: 'ACTIVE' | 'INACTIVE'
  version: string
}

export type VoucherCodeStatus = 'UNBOUND' | 'BOUND' | 'REDEEMED' | 'EXPIRED' | 'VOIDED'

export interface VoucherCodeSummary {
  id: number
  code: string
  voucherId: number
  voucherCode: string
  voucherName: string
  benefitType: 'FIXED_AMOUNT' | 'DISCOUNT'
  faceAmount: number
  discountRate: number
  minSpend: number
  memberId?: number
  memberName?: string
  validFrom: string
  validUntil: string
  status: VoucherCodeStatus
  boundAt?: string
  redeemedAt?: string
  redeemedBillId?: number
  version: string
}

export interface VoucherSettlementOption {
  id: number
  code: string
  voucherName: string
  benefitType: 'FIXED_AMOUNT' | 'DISCOUNT'
  faceAmount: number
  discountRate: number
  minSpend: number
  previewAmount: number
  validUntil: string
  version: string
}

export interface SettlementQuote {
  quoteNo: string
  billId: number
  billVersion: string
  receivableAmount: number
  paymentTotal: number
  assetAmount: number
  externalPaymentAmount: number
  changeAmount: number
  differenceAmount: number
  payments: QuotePayment[]
  assets: SettlementAssetUsage[]
  expiresAt: string
  used: boolean
}

export type CommissionScene = 'SERVICE' | 'CARD_SALE' | 'CARD_CONSUME'
export type CommissionCalculationMode = 'RATE' | 'FIXED' | 'NONE'

export interface CommissionPlan {
  id: number
  code: string
  name: string
  scene: CommissionScene
  calculationMode: CommissionCalculationMode
  rate?: number
  fixedAmount?: number
  storeId?: number
  storeName?: string
  positionId?: number
  positionName?: string
  effectiveFrom: string
  effectiveTo?: string
  status: 'ACTIVE' | 'INACTIVE'
  ruleVersion: number
  version: string
}

export interface CommissionPlanPayload {
  code?: string
  name: string
  scene: CommissionScene
  calculationMode: CommissionCalculationMode
  rate?: number
  fixedAmount?: number
  storeId?: number
  positionId?: number
  effectiveFrom: string
  effectiveTo?: string
}

export interface CommissionSimulationPayload {
  employeeId: number
  storeId: number
  businessDate: string
  performanceAmount: number
  itemCount: number
}

export interface CommissionSimulationResult {
  planId: number
  planCode: string
  planName: string
  planRuleVersion: number
  scene: CommissionScene
  calculationMode: CommissionCalculationMode
  employeeId: number
  employeeName: string
  positionId?: number
  positionName?: string
  storeId: number
  storeName: string
  businessDate: string
  performanceAmount: number
  itemCount: number
  commissionAmount: number
  applicable: boolean
  calculationSteps: string[]
  warnings: string[]
}

export interface CommissionLedgerItem {
  id: number
  ledgerNo: string
  employeeId: number
  employeeName: string
  storeId: number
  storeName: string
  commissionType: CommissionScene
  sourceType: 'BILL' | 'BILL_REVERSAL' | 'CARD_SALE' | 'CARD_REFUND' | 'CARD_EXCHANGE'
  sourceId: number
  sourceNo: string
  sourceLineId?: number
  sourceLineName?: string
  baseAmount: number
  rate?: number
  commissionAmount: number
  calculationStatus: 'CALCULATED' | 'PENDING_RULE'
  planId?: number
  planName?: string
  planRuleVersion?: number
  formulaSnapshot: string
  occurredAt: string
  correlationId: string
  reversedLedgerId?: number
}

export type VisitTaskStatus = 'PENDING' | 'OVERDUE' | 'COMPLETED' | 'CANCELLED'
export type VisitResultCode = 'CONTACTED' | 'NO_ANSWER' | 'DECLINED' | 'FOLLOW_UP'

export interface VisitTaskSummary {
  id: number
  taskNo: string
  memberId: number
  billId: number
  billNo: string
  customerName: string
  maskedMobile: string
  storeId: number
  storeName: string
  dueAt: string
  taskType: 'AFTER_SALE'
  status: VisitTaskStatus
  overdue: boolean
  complaintFlag: boolean
  participantCount: number
  completedCount: number
  conclusion?: string
  settledAt: string
  completedAt?: string
  canceledAt?: string
  cancelReason?: string
  createdAt: string
}

export interface VisitParticipantItem {
  id: number
  employeeId?: number
  employeeName: string
  serviceSummary: string
  status: 'PENDING' | 'COMPLETED'
  completedAt?: string
}

export interface VisitRecordItem {
  id: number
  participantId: number
  employeeId: number
  employeeName: string
  resultCode: VisitResultCode
  satisfactionScore?: number
  complaintFlag: boolean
  content?: string
  nextFollowAt?: string
  createdAt: string
  createdBy: number
  createdByName: string
}

export interface VisitTaskDetail {
  task: VisitTaskSummary
  participants: VisitParticipantItem[]
  records: VisitRecordItem[]
}

export interface VisitRecordPayload {
  employeeId: number
  resultCode: VisitResultCode
  satisfactionScore?: number
  complaintFlag: boolean
  content?: string
  nextFollowAt?: string
}

export type FeedbackStatus = 'OPEN' | 'PROCESSING' | 'RESOLVED' | 'CLOSED'
export type FeedbackActionType = 'CREATED' | 'ASSIGNED' | 'NOTE' | 'RESOLVED' | 'CLOSED' | 'REOPENED'

export interface FeedbackSummary {
  id: number
  feedbackNo: string
  visitTaskId: number
  visitRecordId: number
  memberId: number
  memberName: string
  maskedMobile: string
  billId: number
  billNo: string
  storeId: number
  storeName: string
  channel: 'VISIT'
  score?: number
  content: string
  complaintType: 'SERVICE'
  status: FeedbackStatus
  handlerId?: number
  handlerName?: string
  handleResult?: string
  handledAt?: string
  resolvedAt?: string
  closedAt?: string
  dueHours: number
  dueAt: string
  overdue: boolean
  overdueMinutes: number
  actionCount: number
  createdAt: string
  updatedAt: string
}

export interface FeedbackActionItem {
  id: number
  actionType: FeedbackActionType
  fromStatus?: FeedbackStatus
  toStatus: FeedbackStatus
  handlerId?: number
  handlerName?: string
  content?: string
  createdAt: string
  createdBy: number
  createdByName: string
}

export interface FeedbackDetail {
  feedback: FeedbackSummary
  actions: FeedbackActionItem[]
  attachments: BusinessAttachmentItem[]
}

export interface BusinessAttachmentItem {
  id: number
  fileId: number
  originalName: string
  contentType: 'image/jpeg' | 'image/png' | 'image/webp' | 'application/pdf'
  sizeBytes: number
  sha256: string
  purpose: string
  category: string
  createdAt: string
  createdBy: number
  createdByName: string
}

export interface HandleFeedbackPayload {
  action: 'ASSIGN' | 'NOTE' | 'RESOLVE' | 'CLOSE' | 'REOPEN'
  handlerId?: number
  content?: string
  result?: string
}

export type SettingStatus = 'ACTIVE' | 'DISABLED'

export interface SystemParameterItem {
  id: number
  paramGroup: string
  paramKey: string
  value: string
  valueType: 'STRING' | 'INTEGER' | 'DECIMAL' | 'BOOLEAN' | 'JSON'
  description?: string
  status: SettingStatus
  updatedAt: string
  version: string
}

export interface SatisfactionRule {
  id: number
  ruleName: string
  keywords: string[]
  score: number
  componentMapping: Record<string, string>
  priority: number
  status: SettingStatus
  updatedAt: string
  version: string
}

export interface SatisfactionRulePayload {
  ruleName: string
  keywords: string[]
  score: number
  componentMapping: Record<string, string>
  priority: number
  status: SettingStatus
}

export interface SatisfactionRuleTestResult {
  matched: boolean
  ruleId?: number
  ruleName?: string
  matchedKeyword?: string
  score?: number
  componentMapping: Record<string, string>
  message: string
}
