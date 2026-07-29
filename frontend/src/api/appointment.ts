import { apiRequest } from './http'
import type {
  AppointmentDetail,
  AppointmentSummary,
  AppointmentTransitionPayload,
  AvailabilitySlot,
  CancelReasonOption,
  CreateAppointmentPayload,
  CreatedAppointment,
  UpdateAppointmentPayload,
} from '@/types/api'

export interface AppointmentSearchParams {
  storeId?: number
  startDate?: string
  endDate?: string
  status?: string
}

export function getAppointments(params: AppointmentSearchParams): Promise<AppointmentSummary[]> {
  return apiRequest<AppointmentSummary[]>({ method: 'GET', url: '/appointments', params })
}

export function getAppointment(id: number): Promise<AppointmentDetail> {
  return apiRequest<AppointmentDetail>({ method: 'GET', url: `/appointments/${id}` })
}

export function createAppointment(payload: CreateAppointmentPayload): Promise<CreatedAppointment> {
  return apiRequest<CreatedAppointment>({ method: 'POST', url: '/appointments', data: payload })
}

export function updateAppointment(id: number, payload: UpdateAppointmentPayload): Promise<AppointmentDetail> {
  return apiRequest<AppointmentDetail>({ method: 'PUT', url: `/appointments/${id}`, data: payload })
}

export function transitionAppointment(
  id: number,
  action: 'confirm' | 'arrive' | 'start' | 'complete' | 'cancel' | 'no-show',
  payload: AppointmentTransitionPayload,
): Promise<AppointmentDetail> {
  return apiRequest<AppointmentDetail>({ method: 'POST', url: `/appointments/${id}/${action}`, data: payload })
}

export function getAppointmentCancelReasons(): Promise<CancelReasonOption[]> {
  return apiRequest<CancelReasonOption[]>({ method: 'GET', url: '/appointment-cancel-reasons' })
}

export function getAppointmentAvailability(params: {
  storeId: number
  serviceId: number
  employeeId: number
  date: string
}): Promise<AvailabilitySlot[]> {
  return apiRequest<AvailabilitySlot[]>({ method: 'GET', url: '/appointments/availability', params })
}
