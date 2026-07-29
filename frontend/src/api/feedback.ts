import { apiDownload, apiRequest } from './http'
import type {
  BusinessAttachmentItem,
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

export function uploadServiceFeedbackAttachment(
  feedbackId: number,
  file: File,
): Promise<BusinessAttachmentItem> {
  const data = new FormData()
  data.append('file', file)
  return apiRequest<BusinessAttachmentItem>({
    method: 'POST',
    url: `/service-feedback/${feedbackId}/attachments`,
    data,
  })
}

export function downloadServiceFeedbackAttachment(feedbackId: number, attachmentId: number): Promise<Blob> {
  return apiDownload(`/service-feedback/${feedbackId}/attachments/${attachmentId}/content`)
}

export function removeServiceFeedbackAttachment(feedbackId: number, attachmentId: number): Promise<void> {
  return apiRequest<void>({
    method: 'DELETE',
    url: `/service-feedback/${feedbackId}/attachments/${attachmentId}`,
  })
}
