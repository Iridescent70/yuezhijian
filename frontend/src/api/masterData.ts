import { apiRequest } from './http'
import type {
  AsyncJobItem,
  CategoryOption,
  CreateEmployeePayload,
  CreatedResource,
  CreateServiceItemPayload,
  CreateWorkstationPayload,
  EmployeeSummary,
  PositionOption,
  ServiceItemSummary,
  ServiceItemDetail,
  UpdateServiceItemPayload,
  WorkstationSummary,
} from '@/types/api'

export function getPositions(): Promise<PositionOption[]> {
  return apiRequest<PositionOption[]>({ method: 'GET', url: '/positions' })
}

export function getEmployees(params?: { storeId?: number; keyword?: string }): Promise<EmployeeSummary[]> {
  return apiRequest<EmployeeSummary[]>({ method: 'GET', url: '/employees', params })
}

export function createEmployee(payload: CreateEmployeePayload): Promise<CreatedResource> {
  return apiRequest<CreatedResource>({ method: 'POST', url: '/employees', data: payload })
}

export function getWorkstations(storeId?: number): Promise<WorkstationSummary[]> {
  return apiRequest<WorkstationSummary[]>({ method: 'GET', url: '/workstations', params: { storeId } })
}

export function createWorkstation(payload: CreateWorkstationPayload): Promise<CreatedResource> {
  return apiRequest<CreatedResource>({ method: 'POST', url: '/workstations', data: payload })
}

export function getServiceCategories(): Promise<CategoryOption[]> {
  return apiRequest<CategoryOption[]>({ method: 'GET', url: '/item-categories', params: { type: 'SERVICE' } })
}

export function getServices(params?: { storeId?: number; keyword?: string }): Promise<ServiceItemSummary[]> {
  return apiRequest<ServiceItemSummary[]>({ method: 'GET', url: '/services', params })
}

export function getService(id: number): Promise<ServiceItemDetail> {
  return apiRequest<ServiceItemDetail>({ method: 'GET', url: `/services/${id}` })
}

export function createService(payload: CreateServiceItemPayload): Promise<CreatedResource> {
  return apiRequest<CreatedResource>({ method: 'POST', url: '/services', data: payload })
}

export function updateService(id: number, payload: UpdateServiceItemPayload): Promise<ServiceItemDetail> {
  return apiRequest<ServiceItemDetail>({ method: 'PUT', url: `/services/${id}`, data: payload })
}

export function importServices(file: File): Promise<AsyncJobItem> {
  const data = new FormData()
  data.append('file', file)
  return apiRequest<AsyncJobItem>({ method: 'POST', url: '/services/import', data })
}
