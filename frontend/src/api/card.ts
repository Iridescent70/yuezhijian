import { apiRequest } from './http'
import type {
  CardExchangeQuote,
  CardExchangeResult,
  CardSaleResult,
  CardTypeDetail,
  CreateCardTypePayload,
  MemberCardDetail,
  MemberCardSummary,
} from '@/types/api'

export function getCardTypes(params?: {
  storeId?: number; keyword?: string; status?: string
}): Promise<CardTypeDetail[]> {
  return apiRequest<CardTypeDetail[]>({ method: 'GET', url: '/card-types', params })
}

export function createCardType(payload: CreateCardTypePayload): Promise<CardTypeDetail> {
  return apiRequest<CardTypeDetail>({ method: 'POST', url: '/card-types', data: payload })
}

export function getMemberCards(memberId: number, status?: string): Promise<MemberCardSummary[]> {
  return apiRequest<MemberCardSummary[]>({
    method: 'GET', url: `/members/${memberId}/cards`, params: { status },
  })
}

export function getMemberCard(id: number): Promise<MemberCardDetail> {
  return apiRequest<MemberCardDetail>({ method: 'GET', url: `/member-cards/${id}` })
}

export function purchaseMemberCard(memberId: number, payload: {
  cardTypeId: number; quantity: number; storeId: number; salesEmployeeId?: number;
  paymentMethodId: number; externalReference?: string; startDate?: string; idempotencyKey: string
}): Promise<CardSaleResult> {
  return apiRequest<CardSaleResult>({
    method: 'POST', url: `/members/${memberId}/cards`, data: payload,
  })
}

export function quoteCardExchange(cardId: number, targetCardTypeId: number): Promise<CardExchangeQuote> {
  return apiRequest<CardExchangeQuote>({
    method: 'POST', url: `/member-cards/${cardId}/exchange/quote`, data: { targetCardTypeId },
  })
}

export function executeCardExchange(cardId: number, payload: {
  quoteNo: string
  storeId: number
  employeeId?: number
  payments: Array<{ paymentMethodId: number; amount: number; externalReference?: string }>
  idempotencyKey: string
}): Promise<CardExchangeResult> {
  return apiRequest<CardExchangeResult>({
    method: 'POST', url: `/member-cards/${cardId}/exchange`, data: payload,
  })
}
