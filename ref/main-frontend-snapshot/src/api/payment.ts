import { apiRequest } from './http'
import type {
  CreatePaymentMethodPayload,
  PaymentMethodConfiguration,
  PaymentMethodType,
  UpdatePaymentMethodPayload,
} from '@/types/api'

export function getPaymentMethodConfigurations(params: {
  keyword?: string
  type?: PaymentMethodType
  status?: 'ACTIVE' | 'DISABLED'
  storeId: number
}): Promise<PaymentMethodConfiguration[]> {
  return apiRequest<PaymentMethodConfiguration[]>({
    method: 'GET', url: '/payment-methods/management', params,
  })
}

export function getPaymentMethod(id: number): Promise<PaymentMethodConfiguration> {
  return apiRequest<PaymentMethodConfiguration>({ method: 'GET', url: `/payment-methods/${id}` })
}

export function createPaymentMethod(
  payload: CreatePaymentMethodPayload,
): Promise<PaymentMethodConfiguration> {
  return apiRequest<PaymentMethodConfiguration>({ method: 'POST', url: '/payment-methods', data: payload })
}

export function updatePaymentMethod(
  id: number, payload: UpdatePaymentMethodPayload,
): Promise<PaymentMethodConfiguration> {
  return apiRequest<PaymentMethodConfiguration>({ method: 'PUT', url: `/payment-methods/${id}`, data: payload })
}

export function updatePaymentMethodStore(
  id: number,
  storeId: number,
  payload: { applicable: boolean; enabled: boolean; sortNo: number; version?: string },
): Promise<PaymentMethodConfiguration> {
  return apiRequest<PaymentMethodConfiguration>({
    method: 'PUT', url: `/payment-methods/${id}/stores/${storeId}`, data: payload,
  })
}

export function sortPaymentMethods(payload: {
  storeId: number
  items: Array<{ paymentMethodId: number; sortNo: number; version: string }>
}): Promise<PaymentMethodConfiguration[]> {
  return apiRequest<PaymentMethodConfiguration[]>({
    method: 'PUT', url: '/payment-methods/sort', data: payload,
  })
}
