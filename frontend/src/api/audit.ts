import { apiRequest } from './http'
import type { AuditLogDetail, AuditLogSummary, OperationHistoryItem, PageResult } from '@/types/api'

export function getOperationHistory(
  objectType: 'PRODUCT' | 'SERVICE',
  objectId: number,
): Promise<OperationHistoryItem[]> {
  return apiRequest<OperationHistoryItem[]>({
    method: 'GET',
    url: `/operation-history/${objectType}/${objectId}`,
  })
}

export function getAuditLogs(params: {
  operator?: string
  module?: string
  action?: string
  objectType?: string
  objectId?: string
  result?: string
  occurredFrom?: string
  occurredTo?: string
  page: number
  size: number
}): Promise<PageResult<AuditLogSummary>> {
  return apiRequest<PageResult<AuditLogSummary>>({ method: 'GET', url: '/audit-logs', params })
}

export function getAuditLog(id: number): Promise<AuditLogDetail> {
  return apiRequest<AuditLogDetail>({ method: 'GET', url: `/audit-logs/${id}` })
}
