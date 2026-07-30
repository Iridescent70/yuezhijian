import { apiRequest } from './http'
import type {
  ActiveBanner,
  Banner,
  BannerPositionCode,
  CreateBannerPayload,
  UpdateBannerPayload,
} from '@/types/api'

export function getBanners(params: {
  positionCode?: BannerPositionCode
  keyword?: string
  status?: 'ACTIVE' | 'DISABLED'
}): Promise<Banner[]> {
  return apiRequest<Banner[]>({ method: 'GET', url: '/banners', params })
}

export function getActiveBanners(positionCode: BannerPositionCode): Promise<ActiveBanner[]> {
  return apiRequest<ActiveBanner[]>({ method: 'GET', url: '/banners/active', params: { positionCode } })
}

export function getBanner(id: number): Promise<Banner> {
  return apiRequest<Banner>({ method: 'GET', url: `/banners/${id}` })
}

export function createBanner(payload: CreateBannerPayload, file: File): Promise<Banner> {
  const form = new FormData()
  form.append('request', new Blob([JSON.stringify(payload)], { type: 'application/json' }))
  form.append('file', file)
  return apiRequest<Banner>({ method: 'POST', url: '/banners', data: form })
}

export function updateBanner(id: number, payload: UpdateBannerPayload): Promise<Banner> {
  return apiRequest<Banner>({ method: 'PUT', url: `/banners/${id}`, data: payload })
}

export function replaceBannerImage(id: number, version: string, file: File): Promise<Banner> {
  const form = new FormData()
  form.append('file', file)
  return apiRequest<Banner>({
    method: 'PUT', url: `/banners/${id}/image`, params: { version }, data: form,
  })
}

export function managementBannerImageUrl(banner: Pick<Banner, 'id' | 'version'>): string {
  return `/api/v1/banners/${banner.id}/image?v=${encodeURIComponent(banner.version)}`
}

export function activeBannerImageUrl(banner: Pick<ActiveBanner, 'id' | 'version'>): string {
  return `/api/v1/banners/active/${banner.id}/image?v=${encodeURIComponent(banner.version)}`
}
