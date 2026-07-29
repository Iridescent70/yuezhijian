import { apiRequest } from './http'
import type {
  CancelReason,
  CancelReasonBusinessType,
  CreateCancelReasonPayload,
  UpdateCancelReasonPayload,
} from '@/types/api'

export function getCancelReasons(params: {
  businessType?: CancelReasonBusinessType
  keyword?: string
  status?: 'ACTIVE' | 'DISABLED'
}): Promise<CancelReason[]> {
  return apiRequest<CancelReason[]>({ method: 'GET', url: '/cancel-reasons', params })
}

export function getCancelReason(id: number): Promise<CancelReason> {
  return apiRequest<CancelReason>({ method: 'GET', url: `/cancel-reasons/${id}` })
}

export function createCancelReason(payload: CreateCancelReasonPayload): Promise<CancelReason> {
  return apiRequest<CancelReason>({ method: 'POST', url: '/cancel-reasons', data: payload })
}

export function updateCancelReason(
  id: number,
  payload: UpdateCancelReasonPayload,
): Promise<CancelReason> {
  return apiRequest<CancelReason>({ method: 'PUT', url: `/cancel-reasons/${id}`, data: payload })
}
