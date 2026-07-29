import { apiRequest } from './http'
import type {
  CreateMemberPayload,
  CreatedMember,
  MemberDetail,
  MemberSummary,
  PageResult,
} from '@/types/api'

export interface MemberSearchParams {
  keyword?: string
  storeId?: number
  status?: string
  page: number
  size: number
}

export function searchMembers(params: MemberSearchParams): Promise<PageResult<MemberSummary>> {
  return apiRequest<PageResult<MemberSummary>>({ method: 'GET', url: '/members', params })
}

export function getMember(id: number): Promise<MemberDetail> {
  return apiRequest<MemberDetail>({ method: 'GET', url: `/members/${id}` })
}

export function createMember(payload: CreateMemberPayload): Promise<CreatedMember> {
  return apiRequest<CreatedMember>({ method: 'POST', url: '/members', data: payload })
}
