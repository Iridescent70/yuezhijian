import { apiRequest } from './http'
import type { RoleSummary, StoreSummary, WorkbenchOverview } from '@/types/api'

export function getWorkbenchOverview(): Promise<WorkbenchOverview> {
  return apiRequest<WorkbenchOverview>({ method: 'GET', url: '/workbench/overview' })
}

export function getStores(): Promise<StoreSummary[]> {
  return apiRequest<StoreSummary[]>({ method: 'GET', url: '/stores' })
}

export function getRoles(): Promise<RoleSummary[]> {
  return apiRequest<RoleSummary[]>({ method: 'GET', url: '/roles' })
}
