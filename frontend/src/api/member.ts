import { apiRequest } from './http'
import type {
  CreateMemberPayload,
  CreatedMember,
  MemberDetail,
  MemberTagOption,
  MemberSummary,
  PageResult,
  ChangeMemberStatusPayload,
  UpdateMemberPayload,
  UpdateMemberTagsPayload,
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

export function updateMember(id: number, payload: UpdateMemberPayload): Promise<MemberDetail> {
  return apiRequest<MemberDetail>({ method: 'PUT', url: `/members/${id}`, data: payload })
}

export function changeMemberStatus(id: number, payload: ChangeMemberStatusPayload): Promise<MemberDetail> {
  return apiRequest<MemberDetail>({ method: 'POST', url: `/members/${id}/status`, data: payload })
}

export function getMemberTagOptions(): Promise<MemberTagOption[]> {
  return apiRequest<MemberTagOption[]>({ method: 'GET', url: '/member-tags' })
}

export function updateMemberTags(id: number, payload: UpdateMemberTagsPayload): Promise<MemberDetail> {
  return apiRequest<MemberDetail>({ method: 'PUT', url: `/members/${id}/tags`, data: payload })
}
