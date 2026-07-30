import { apiRequest } from './http'
import type { CreateServiceAreaPayload, ServiceArea, UpdateServiceAreaPayload } from '@/types/api'

export function getServiceAreas(params: {
  storeId?: number
  keyword?: string
  status?: 'ACTIVE' | 'DISABLED'
}): Promise<ServiceArea[]> {
  return apiRequest<ServiceArea[]>({ method: 'GET', url: '/service-areas', params })
}

export function getServiceArea(id: number): Promise<ServiceArea> {
  return apiRequest<ServiceArea>({ method: 'GET', url: `/service-areas/${id}` })
}

export function createServiceArea(payload: CreateServiceAreaPayload): Promise<ServiceArea> {
  return apiRequest<ServiceArea>({ method: 'POST', url: '/service-areas', data: payload })
}

export function updateServiceArea(
  id: number,
  payload: UpdateServiceAreaPayload,
): Promise<ServiceArea> {
  return apiRequest<ServiceArea>({ method: 'PUT', url: `/service-areas/${id}`, data: payload })
}
