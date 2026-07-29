import { apiRequest } from './http'
import type {
  BillDetail,
  BillSummary,
  CreatedBill,
  PaymentMethodOption,
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

export function getPaymentMethods(storeId: number): Promise<PaymentMethodOption[]> {
  return apiRequest<PaymentMethodOption[]>({ method: 'GET', url: '/payment-methods', params: { storeId } })
}

export function quoteSettlement(id: number, payments: Array<{
  paymentMethodId: number; amount: number; externalReference?: string
}>): Promise<SettlementQuote> {
  return apiRequest<SettlementQuote>({ method: 'POST', url: `/bills/${id}/settlement/quote`, data: { payments } })
}

export function settleBill(id: number, quoteNo: string, idempotencyKey: string): Promise<BillDetail> {
  return apiRequest<BillDetail>({ method: 'POST', url: `/bills/${id}/settle`, data: { quoteNo, idempotencyKey } })
}

export function voidBill(id: number, payload: {
  reasonCode: string; note?: string; version: string
}): Promise<BillDetail> {
  return apiRequest<BillDetail>({ method: 'POST', url: `/bills/${id}/void`, data: payload })
}
