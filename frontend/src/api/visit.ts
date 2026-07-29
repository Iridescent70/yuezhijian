import { apiRequest } from './http'
import type {
  VisitRecordPayload,
  VisitTaskDetail,
  VisitTaskStatus,
  VisitTaskSummary,
} from '@/types/api'

export function getVisitTasks(params?: {
  storeId?: number
  employeeId?: number
  status?: VisitTaskStatus
  dueDate?: string
  keyword?: string
}): Promise<VisitTaskSummary[]> {
  return apiRequest<VisitTaskSummary[]>({ method: 'GET', url: '/visit-tasks', params })
}

export function getVisitTask(id: number): Promise<VisitTaskDetail> {
  return apiRequest<VisitTaskDetail>({ method: 'GET', url: `/visit-tasks/${id}` })
}

export function addVisitRecord(id: number, payload: VisitRecordPayload): Promise<VisitTaskDetail> {
  return apiRequest<VisitTaskDetail>({ method: 'POST', url: `/visit-tasks/${id}/records`, data: payload })
}

export function completeVisitTask(id: number, conclusion: string): Promise<VisitTaskDetail> {
  return apiRequest<VisitTaskDetail>({ method: 'POST', url: `/visit-tasks/${id}/complete`, data: { conclusion } })
}
