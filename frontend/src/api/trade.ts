import { apiRequest } from './http'
import type {
  BillDetail,
  BillSummary,
  CardSettlementOption,
  CreatedBill,
  PaymentMethodOption,
  SettlementAssetOptions,
  SettlementQuote,
} from '@/types/api'

export function getBills(params: {
  storeId?: number; startDate?: string; endDate?: string; status?: string; keyword?: string
}): Promise<BillSummary[]> {
  return apiRequest<BillSummary[]>({ method: 'GET', url: '/bills', params })
}

export function getBill(id: number): Promise<BillDetail> {
  return apiRequest<BillDetail>({ method: 'GET', url: `/bills/${id}` })
}

export function createBill(payload: {
  memberId?: number; guestName?: string; guestMobile?: string; storeId: number;
  sourceType: string; personCount: number; note?: string; idempotencyKey: string
}): Promise<CreatedBill> {
  return apiRequest<CreatedBill>({ method: 'POST', url: '/bills', data: payload })
}

export function createBillFromAppointment(appointmentId: number): Promise<CreatedBill> {
  return apiRequest<CreatedBill>({
    method: 'POST', url: `/appointments/${appointmentId}/create-bill`, data: { copyServices: true },
  })
}

export function addBillLine(id: number, payload: {
  serviceId: number; quantity: number; employeeId?: number; note?: string; version: string
}): Promise<BillDetail> {
  return apiRequest<BillDetail>({ method: 'POST', url: `/bills/${id}/lines`, data: payload })
}

export function updateBillLine(id: number, lineId: number, payload: {
  quantity: number; employeeId?: number; note?: string; version: string
}): Promise<BillDetail> {
  return apiRequest<BillDetail>({ method: 'PUT', url: `/bills/${id}/lines/${lineId}`, data: payload })
}

export function removeBillLine(id: number, lineId: number, version: string): Promise<BillDetail> {
  return apiRequest<BillDetail>({
    method: 'DELETE', url: `/bills/${id}/lines/${lineId}`, params: { version },
  })
}

export function applyBillDiscount(id: number, payload: {
  discountType: 'AMOUNT' | 'RATE'; value: number; reason: string; version: string
}): Promise<BillDetail> {
  return apiRequest<BillDetail>({ method: 'POST', url: `/bills/${id}/discounts`, data: payload })
}

export function getPaymentMethods(storeId: number): Promise<PaymentMethodOption[]> {
  return apiRequest<PaymentMethodOption[]>({ method: 'GET', url: '/payment-methods', params: { storeId } })
}

export function getCardSettlementOptions(id: number): Promise<CardSettlementOption[]> {
  return apiRequest<CardSettlementOption[]>({ method: 'GET', url: `/bills/${id}/card-options` })
}

export function getSettlementAssetOptions(id: number): Promise<SettlementAssetOptions> {
  return apiRequest<SettlementAssetOptions>({ method: 'GET', url: `/bills/${id}/asset-options` })
}

export interface SettlementQuotePayload {
  payments: Array<{ paymentMethodId: number; amount: number; externalReference?: string }>
  balanceAmount?: number
  points?: number
  cards?: Array<{ billLineId: number; memberCardId: number }>
}

export function quoteSettlement(id: number, payload: SettlementQuotePayload): Promise<SettlementQuote> {
  return apiRequest<SettlementQuote>({ method: 'POST', url: `/bills/${id}/settlement/quote`, data: payload })
}

export function settleBill(id: number, quoteNo: string, idempotencyKey: string): Promise<BillDetail> {
  return apiRequest<BillDetail>({ method: 'POST', url: `/bills/${id}/settle`, data: { quoteNo, idempotencyKey } })
}

export function voidBill(id: number, payload: {
  reasonCode: string; note?: string; version: string
}): Promise<BillDetail> {
  return apiRequest<BillDetail>({ method: 'POST', url: `/bills/${id}/void`, data: payload })
}
