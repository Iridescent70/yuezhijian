import request from '@/config/axios'

export interface MemberProfileVO {
  id?: number
  memberUserId?: number
  memberNo?: string
  fullName?: string
  maskedMobile?: string
  membershipCardNo?: string
  joinStoreDeptId?: number
  joinStoreName?: string
  ownerStoreDeptId?: number
  ownerStoreName?: string
  advisorUserId?: number
  advisorName?: string
  sourceType?: 'MANUAL' | 'IMPORT' | 'ONLINE' | 'REFERRAL'
  special?: boolean
  lifecycleStatus?: 'ACTIVE' | 'FROZEN' | 'LOST'
  frozenAt?: Date
  freezeReason?: string
  version?: number
  createTime?: Date
  updateTime?: Date
}

export interface MemberProfileCreateReqVO {
  fullName?: string
  nickname?: string
  mobile?: string
  sex?: number
  birthday?: string
  email?: string
  joinStoreDeptId?: number
  ownerStoreDeptId?: number
  advisorUserId?: number
  sourceType?: 'MANUAL' | 'IMPORT' | 'ONLINE' | 'REFERRAL'
  membershipCardNo?: string
}

export interface MemberProfilePageReqVO extends PageParam {
  memberNo?: string
  fullName?: string
  mobile?: string
  ownerStoreDeptId?: number
  lifecycleStatus?: 'ACTIVE' | 'FROZEN' | 'LOST'
}

export const getMemberProfilePage = (
  params: MemberProfilePageReqVO
): Promise<PageResult<MemberProfileVO[]>> => {
  return request.get({ url: '/yuezhijian/member/page', params })
}

export const createMemberProfile = (data: MemberProfileCreateReqVO): Promise<MemberProfileVO> => {
  return request.post({ url: '/yuezhijian/member/create', data })
}
