import { apiDownload, apiRequest } from './http'
import type { AsyncJobItem, AsyncJobStatus, PageResult } from '@/types/api'

export interface JobQuery {
  jobType?: string
  status?: AsyncJobStatus
  page?: number
  size?: number
}

export function createExport(payload: {
  exportType: 'SERVICE_FEEDBACK'
  status?: string
  overdue?: boolean
}): Promise<AsyncJobItem> {
  return apiRequest<AsyncJobItem>({ method: 'POST', url: '/exports', data: payload })
}

export function getJobs(params: JobQuery): Promise<PageResult<AsyncJobItem>> {
  return apiRequest<PageResult<AsyncJobItem>>({ method: 'GET', url: '/jobs', params })
}

export function getJob(id: number): Promise<AsyncJobItem> {
  return apiRequest<AsyncJobItem>({ method: 'GET', url: `/jobs/${id}` })
}

export function cancelJob(id: number): Promise<AsyncJobItem> {
  return apiRequest<AsyncJobItem>({ method: 'POST', url: `/jobs/${id}/cancel` })
}

export function downloadJobResult(id: number): Promise<Blob> {
  return apiDownload(`/jobs/${id}/result`)
}
