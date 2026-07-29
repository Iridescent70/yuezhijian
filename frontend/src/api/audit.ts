import { apiRequest } from './http'
import type { OperationHistoryItem } from '@/types/api'

export function getOperationHistory(
  objectType: 'PRODUCT' | 'SERVICE',
  objectId: number,
): Promise<OperationHistoryItem[]> {
  return apiRequest<OperationHistoryItem[]>({
    method: 'GET',
    url: `/operation-history/${objectType}/${objectId}`,
  })
}
