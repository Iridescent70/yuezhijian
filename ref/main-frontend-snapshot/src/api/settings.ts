import { apiRequest } from './http'
import type {
  SatisfactionRule,
  SatisfactionRulePayload,
  SatisfactionRuleTestResult,
  SettingStatus,
  SystemParameterItem,
} from '@/types/api'

export function getSystemParameters(group?: string): Promise<SystemParameterItem[]> {
  return apiRequest<SystemParameterItem[]>({ method: 'GET', url: '/system-parameters', params: { group } })
}

export function updateSystemParameter(
  id: number, payload: { value: string; status: SettingStatus; version: string },
): Promise<SystemParameterItem> {
  return apiRequest<SystemParameterItem>({ method: 'PUT', url: `/system-parameters/${id}`, data: payload })
}

export function getSatisfactionRules(status?: SettingStatus): Promise<SatisfactionRule[]> {
  return apiRequest<SatisfactionRule[]>({ method: 'GET', url: '/satisfaction-rules', params: { status } })
}

export function createSatisfactionRule(payload: SatisfactionRulePayload): Promise<SatisfactionRule> {
  return apiRequest<SatisfactionRule>({ method: 'POST', url: '/satisfaction-rules', data: payload })
}

export function updateSatisfactionRule(
  id: number, payload: SatisfactionRulePayload & { version: string },
): Promise<SatisfactionRule> {
  return apiRequest<SatisfactionRule>({ method: 'PUT', url: `/satisfaction-rules/${id}`, data: payload })
}

export function testSatisfactionRule(text: string): Promise<SatisfactionRuleTestResult> {
  return apiRequest<SatisfactionRuleTestResult>({
    method: 'POST', url: '/satisfaction-rules/test', data: { text },
  })
}
