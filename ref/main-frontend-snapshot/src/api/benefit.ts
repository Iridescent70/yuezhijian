import { apiRequest } from './http'
import type { VoucherCodeSummary, VoucherDefinition } from '@/types/api'

export function getVoucherDefinitions(params?: { keyword?: string; status?: string }): Promise<VoucherDefinition[]> {
  return apiRequest<VoucherDefinition[]>({ method: 'GET', url: '/vouchers', params })
}

export function createVoucherDefinition(payload: {
  code: string
  name: string
  benefitType: 'FIXED_AMOUNT' | 'DISCOUNT'
  faceAmount: number
  discountRate: number
  minSpend: number
  validDays: number
  commissionRule?: string
}): Promise<VoucherDefinition> {
  return apiRequest<VoucherDefinition>({ method: 'POST', url: '/vouchers', data: payload })
}

export function updateVoucherDefinition(id: number, payload: {
  name: string
  benefitType: 'FIXED_AMOUNT' | 'DISCOUNT'
  faceAmount: number
  discountRate: number
  minSpend: number
  validDays: number
  commissionRule?: string
  status: 'ACTIVE' | 'INACTIVE'
  version: string
}): Promise<VoucherDefinition> {
  return apiRequest<VoucherDefinition>({ method: 'PUT', url: `/vouchers/${id}`, data: payload })
}

export function getVoucherCodes(params?: {
  memberId?: number; status?: string; keyword?: string
}): Promise<VoucherCodeSummary[]> {
  return apiRequest<VoucherCodeSummary[]>({ method: 'GET', url: '/voucher-codes', params })
}

export function issueVoucherCodes(payload: {
  voucherId: number; count: number; memberId?: number; idempotencyKey: string
}): Promise<VoucherCodeSummary[]> {
  return apiRequest<VoucherCodeSummary[]>({ method: 'POST', url: '/voucher-code-issues', data: payload })
}

export function bindVoucherCode(
  code: string,
  memberId: number,
  idempotencyKey: string,
): Promise<VoucherCodeSummary> {
  return apiRequest<VoucherCodeSummary>({
    method: 'POST', url: `/voucher-codes/${code}/bind`, data: { memberId, idempotencyKey },
  })
}
