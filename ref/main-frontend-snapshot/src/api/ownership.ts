import { apiRequest } from './http'
import type {
  CreateOwnershipAdjustmentPayload,
  OwnershipAdjustment,
  ReviewOwnershipAdjustmentPayload,
} from '@/types/api'

export function getOwnershipAdjustments(params?: {
  memberId?: number
  approvalStatus?: string
  executionStatus?: string
}): Promise<OwnershipAdjustment[]> {
  return apiRequest<OwnershipAdjustment[]>({ method: 'GET', url: '/ownership-adjustments', params })
}

export function getOwnershipAdjustment(id: number): Promise<OwnershipAdjustment> {
  return apiRequest<OwnershipAdjustment>({ method: 'GET', url: `/ownership-adjustments/${id}` })
}

export function createOwnershipAdjustment(
  memberId: number,
  payload: CreateOwnershipAdjustmentPayload,
): Promise<OwnershipAdjustment> {
  return apiRequest<OwnershipAdjustment>({
    method: 'POST',
    url: `/members/${memberId}/ownership-adjustments`,
    data: payload,
  })
}

export function approveOwnershipAdjustment(
  id: number,
  payload: ReviewOwnershipAdjustmentPayload,
): Promise<OwnershipAdjustment> {
  return apiRequest<OwnershipAdjustment>({
    method: 'POST',
    url: `/ownership-adjustments/${id}/approve`,
    data: payload,
  })
}

export function rejectOwnershipAdjustment(
  id: number,
  payload: ReviewOwnershipAdjustmentPayload,
): Promise<OwnershipAdjustment> {
  return apiRequest<OwnershipAdjustment>({
    method: 'POST',
    url: `/ownership-adjustments/${id}/reject`,
    data: payload,
  })
}
