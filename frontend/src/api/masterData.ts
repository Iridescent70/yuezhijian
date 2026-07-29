import { apiRequest } from './http'
import type {
  AsyncJobItem,
  CategoryOption,
  CreateCategoryPayload,
  CreateEmployeePayload,
  CreatePositionPayload,
  CreatedResource,
  CreateServiceItemPayload,
  CreateWorkstationPayload,
  CreateUnitPayload,
  EmployeeSummary,
  PositionOption,
  ServiceItemSummary,
  ServiceItemDetail,
  UpdateServiceItemPayload,
  UpdateEmployeePayload,
  UpdatePositionPayload,
  UpdateWorkstationPayload,
  UpdateCategoryPayload,
  UpdateUnitPayload,
  UnitOption,
  WorkstationSummary,
} from '@/types/api'

export function getPositions(activeOnly = true): Promise<PositionOption[]> {
  return apiRequest<PositionOption[]>({ method: 'GET', url: '/positions', params: { activeOnly } })
}

export function getPosition(id: number): Promise<PositionOption> {
  return apiRequest<PositionOption>({ method: 'GET', url: `/positions/${id}` })
}

export function createPosition(payload: CreatePositionPayload): Promise<CreatedResource> {
  return apiRequest<CreatedResource>({ method: 'POST', url: '/positions', data: payload })
}

export function updatePosition(id: number, payload: UpdatePositionPayload): Promise<PositionOption> {
  return apiRequest<PositionOption>({ method: 'PUT', url: `/positions/${id}`, data: payload })
}

export function getEmployees(params?: { storeId?: number; keyword?: string }): Promise<EmployeeSummary[]> {
  return apiRequest<EmployeeSummary[]>({ method: 'GET', url: '/employees', params })
}

export function createEmployee(payload: CreateEmployeePayload): Promise<CreatedResource> {
  return apiRequest<CreatedResource>({ method: 'POST', url: '/employees', data: payload })
}

export function getEmployee(id: number): Promise<EmployeeSummary> {
  return apiRequest<EmployeeSummary>({ method: 'GET', url: `/employees/${id}` })
}

export function updateEmployee(id: number, payload: UpdateEmployeePayload): Promise<EmployeeSummary> {
  return apiRequest<EmployeeSummary>({ method: 'PUT', url: `/employees/${id}`, data: payload })
}

export function getWorkstations(storeId?: number): Promise<WorkstationSummary[]> {
  return apiRequest<WorkstationSummary[]>({ method: 'GET', url: '/workstations', params: { storeId } })
}

export function createWorkstation(payload: CreateWorkstationPayload): Promise<CreatedResource> {
  return apiRequest<CreatedResource>({ method: 'POST', url: '/workstations', data: payload })
}

export function getWorkstation(id: number): Promise<WorkstationSummary> {
  return apiRequest<WorkstationSummary>({ method: 'GET', url: `/workstations/${id}` })
}

export function updateWorkstation(id: number, payload: UpdateWorkstationPayload): Promise<WorkstationSummary> {
  return apiRequest<WorkstationSummary>({ method: 'PUT', url: `/workstations/${id}`, data: payload })
}

export function getServiceCategories(): Promise<CategoryOption[]> {
  return apiRequest<CategoryOption[]>({ method: 'GET', url: '/item-categories', params: { type: 'SERVICE' } })
}

export function getItemCategories(type: 'PRODUCT' | 'SERVICE', activeOnly = true): Promise<CategoryOption[]> {
  return apiRequest<CategoryOption[]>({ method: 'GET', url: '/item-categories', params: { type, activeOnly } })
}

export function getItemCategory(id: number): Promise<CategoryOption> {
  return apiRequest<CategoryOption>({ method: 'GET', url: `/item-categories/${id}` })
}

export function createItemCategory(payload: CreateCategoryPayload): Promise<CreatedResource> {
  return apiRequest<CreatedResource>({ method: 'POST', url: '/item-categories', data: payload })
}

export function updateItemCategory(id: number, payload: UpdateCategoryPayload): Promise<CategoryOption> {
  return apiRequest<CategoryOption>({ method: 'PUT', url: `/item-categories/${id}`, data: payload })
}

export function getCatalogUnits(activeOnly = true): Promise<UnitOption[]> {
  return apiRequest<UnitOption[]>({ method: 'GET', url: '/units', params: { activeOnly } })
}

export function getCatalogUnit(id: number): Promise<UnitOption> {
  return apiRequest<UnitOption>({ method: 'GET', url: `/units/${id}` })
}

export function createCatalogUnit(payload: CreateUnitPayload): Promise<CreatedResource> {
  return apiRequest<CreatedResource>({ method: 'POST', url: '/units', data: payload })
}

export function updateCatalogUnit(id: number, payload: UpdateUnitPayload): Promise<UnitOption> {
  return apiRequest<UnitOption>({ method: 'PUT', url: `/units/${id}`, data: payload })
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
