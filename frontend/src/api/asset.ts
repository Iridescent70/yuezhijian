import { apiRequest } from './http'
import type {
  BalanceAccount,
  BalanceLedgerItem,
  PointAccount,
  PointLedgerItem,
  RechargeOrder,
  RechargeQuote,
} from '@/types/api'

export function getBalanceAccount(memberId: number): Promise<BalanceAccount> {
  return apiRequest<BalanceAccount>({ method: 'GET', url: `/members/${memberId}/balance-account` })
}

export function getBalanceLedgers(memberId: number, limit = 100): Promise<BalanceLedgerItem[]> {
  return apiRequest<BalanceLedgerItem[]>({
    method: 'GET', url: `/members/${memberId}/balance-ledgers`, params: { limit },
  })
}

export function getPointAccount(memberId: number): Promise<PointAccount> {
  return apiRequest<PointAccount>({ method: 'GET', url: `/members/${memberId}/point-account` })
}

export function getPointLedgers(memberId: number, limit = 100): Promise<PointLedgerItem[]> {
  return apiRequest<PointLedgerItem[]>({
    method: 'GET', url: `/members/${memberId}/point-ledgers`, params: { limit },
  })
}

export function quoteRecharge(memberId: number, payload: {
  rechargeAmount: number; giftAmount: number; paymentMethodId: number
}): Promise<RechargeQuote> {
  return apiRequest<RechargeQuote>({
    method: 'POST', url: `/members/${memberId}/recharges/quote`, data: payload,
  })
}

export function createRecharge(memberId: number, payload: {
  quoteNo: string; storeId: number; salesEmployeeId?: number;
  externalReference?: string; idempotencyKey: string
}): Promise<RechargeOrder> {
  return apiRequest<RechargeOrder>({
    method: 'POST', url: `/members/${memberId}/recharges`, data: payload,
  })
}

export function confirmRecharge(id: number, version: string): Promise<RechargeOrder> {
  return apiRequest<RechargeOrder>({
    method: 'POST', url: `/recharges/${id}/confirm`, data: { version },
  })
}

export function adjustPoints(memberId: number, payload: {
  changePoints: number; reason: string; idempotencyKey: string
}): Promise<PointAccount> {
  return apiRequest<PointAccount>({
    method: 'POST', url: `/members/${memberId}/points/adjustments`, data: payload,
  })
}
