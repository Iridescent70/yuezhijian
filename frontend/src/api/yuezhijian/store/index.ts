import request from '@/config/axios'

export interface StoreProfileVO {
  id?: number
  deptId?: number
  deptName?: string
  storeCode?: string
  storeLevel?: string
  province?: string
  city?: string
  district?: string
  address?: string
  longitude?: number
  latitude?: number
  businessHoursJson?: string
  version?: number
  createTime?: Date
  updateTime?: Date
}

export const getStoreProfileList = (): Promise<StoreProfileVO[]> => {
  return request.get({ url: '/yuezhijian/store-profile/list' })
}

export const saveStoreProfile = (data: StoreProfileVO): Promise<StoreProfileVO> => {
  return request.put({ url: '/yuezhijian/store-profile/save', data })
}
