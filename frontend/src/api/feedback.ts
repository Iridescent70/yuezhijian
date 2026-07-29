import { apiRequest } from './http'
import type {
  FeedbackDetail,
  FeedbackStatus,
  FeedbackSummary,
  HandleFeedbackPayload,
} from '@/types/api'

export function getServiceFeedback(params?: {
  storeId?: number
  handlerId?: number
  score?: number
  status?: FeedbackStatus
  overdue?: boolean
  keyword?: string
}): Promise<FeedbackSummary[]> {
  return apiRequest<FeedbackSummary[]>({ method: 'GET', url: '/service-feedback', params })
}

export function getServiceFeedbackDetail(id: number): Promise<FeedbackDetail> {
  return apiRequest<FeedbackDetail>({ method: 'GET', url: `/service-feedback/${id}` })
}

export function handleServiceFeedback(id: number, payload: HandleFeedbackPayload): Promise<FeedbackDetail> {
  return apiRequest<FeedbackDetail>({ method: 'POST', url: `/service-feedback/${id}/handle`, data: payload })
}
