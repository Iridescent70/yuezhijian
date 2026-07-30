import { apiRequest } from './http'
import type {
  CommissionLedgerItem,
  CommissionPlan,
  CommissionPlanPayload,
  CommissionSimulationPayload,
  CommissionSimulationResult,
} from '@/types/api'

export function getCommissionPlans(params?: {
  keyword?: string; status?: string
}): Promise<CommissionPlan[]> {
  return apiRequest<CommissionPlan[]>({ method: 'GET', url: '/commission-plans', params })
}

export function createCommissionPlan(payload: CommissionPlanPayload): Promise<CommissionPlan> {
  return apiRequest<CommissionPlan>({ method: 'POST', url: '/commission-plans', data: payload })
}

export function updateCommissionPlan(
  id: number, payload: CommissionPlanPayload & { status: string; version: string },
): Promise<CommissionPlan> {
  return apiRequest<CommissionPlan>({ method: 'PUT', url: `/commission-plans/${id}`, data: payload })
}

export function simulateCommissionPlan(
  id: number, payload: CommissionSimulationPayload,
): Promise<CommissionSimulationResult> {
  return apiRequest<CommissionSimulationResult>({
    method: 'POST', url: `/commission-plans/${id}/simulate`, data: payload,
  })
}

export function getCommissionLedgers(params?: {
  employeeId?: number; storeId?: number; startDate?: string; endDate?: string;
  direction?: string; calculationStatus?: string
}): Promise<CommissionLedgerItem[]> {
  return apiRequest<CommissionLedgerItem[]>({ method: 'GET', url: '/commission-ledgers', params })
}
