import { apiRequest } from './http'
import type {
  CardExchangeQuote,
  CardExchangeResult,
  CardRefundQuote,
  CardRefundRequestDetail,
  CardRefundRequestSummary,
  CardTransferResult,
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

export function transferMemberCard(cardId: number, payload: {
  recipientMemberId: number
  expiresAt: string
  storeId: number
  employeeId?: number
  reason: string
  sourceCardVersion: string
  idempotencyKey: string
}): Promise<CardTransferResult> {
  return apiRequest<CardTransferResult>({
    method: 'POST', url: `/member-cards/${cardId}/transfer`, data: payload,
  })
}

export function quoteCardRefund(cardId: number, feeAmount: number): Promise<CardRefundQuote> {
  return apiRequest<CardRefundQuote>({
    method: 'POST', url: `/member-cards/${cardId}/refund-requests/quote`, data: { feeAmount },
  })
}

export function submitCardRefund(cardId: number, payload: {
  quoteNo: string
  refundMethodId?: number
  storeId: number
  employeeId?: number
  reason: string
  idempotencyKey: string
}): Promise<CardRefundRequestDetail> {
  return apiRequest<CardRefundRequestDetail>({
    method: 'POST', url: `/member-cards/${cardId}/refund-requests`, data: payload,
  })
}

export function getCardRefundRequests(status?: string): Promise<CardRefundRequestSummary[]> {
  return apiRequest<CardRefundRequestSummary[]>({
    method: 'GET', url: '/card-refund-requests', params: { status },
  })
}

export function getCardRefundRequest(id: number): Promise<CardRefundRequestDetail> {
  return apiRequest<CardRefundRequestDetail>({ method: 'GET', url: `/card-refund-requests/${id}` })
}

export function reviewCardRefund(
  id: number, approved: boolean, comment: string | undefined, version: string,
): Promise<CardRefundRequestDetail> {
  return apiRequest<CardRefundRequestDetail>({
    method: 'POST', url: `/card-refund-requests/${id}/review`, data: { approved, comment, version },
  })
}

export function executeCardRefund(
  id: number, version: string, externalRefundReference: string | undefined, idempotencyKey: string,
): Promise<CardRefundRequestDetail> {
  return apiRequest<CardRefundRequestDetail>({
    method: 'POST', url: `/card-refund-requests/${id}/execute`,
    data: { version, externalRefundReference, idempotencyKey },
  })
}
