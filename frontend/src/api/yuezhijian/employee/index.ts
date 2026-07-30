import request from '@/config/axios'

export interface EmployeeProfileVO {
  id?: number
  userId?: number
  nickname?: string
  postIds?: number[]
  employeeNo?: string
  primaryStoreDeptId?: number
  primaryStoreName?: string
  hireDate?: string
  leaveDate?: string
  canService?: boolean
  canSell?: boolean
  employmentStatus?: 'ACTIVE' | 'LEAVE'
  version?: number
  createTime?: Date
  updateTime?: Date
}

export const getEmployeeProfileList = (): Promise<EmployeeProfileVO[]> => {
  return request.get({ url: '/yuezhijian/employee-profile/list' })
}

export const saveEmployeeProfile = (data: EmployeeProfileVO): Promise<EmployeeProfileVO> => {
  return request.put({ url: '/yuezhijian/employee-profile/save', data })
}
