import { apiRequest } from './http'
import type {
  ColorStyle,
  ColorStyleAsset,
  ColorStyleCategory,
  CreateColorStyleCategoryPayload,
  CreateColorStylePayload,
  UpdateColorStyleCategoryPayload,
  UpdateColorStylePayload,
  PageResult,
} from '@/types/api'

export function getColorStyleCategories(params: {
  keyword?: string
  status?: 'ACTIVE' | 'DISABLED'
} = {}): Promise<ColorStyleCategory[]> {
  return apiRequest<ColorStyleCategory[]>({ method: 'GET', url: '/color-style-categories', params })
}

export function createColorStyleCategory(
  payload: CreateColorStyleCategoryPayload,
): Promise<ColorStyleCategory> {
  return apiRequest<ColorStyleCategory>({ method: 'POST', url: '/color-style-categories', data: payload })
}

export function updateColorStyleCategory(
  id: number,
  payload: UpdateColorStyleCategoryPayload,
): Promise<ColorStyleCategory> {
  return apiRequest<ColorStyleCategory>({
    method: 'PUT', url: `/color-style-categories/${id}`, data: payload,
  })
}

export function replaceColorStyleCategoryImage(
  id: number, version: string, file: File,
): Promise<ColorStyleCategory> {
  const form = new FormData()
  form.append('file', file)
  return apiRequest<ColorStyleCategory>({
    method: 'PUT', url: `/color-style-categories/${id}/image`, params: { version }, data: form,
  })
}

export function colorStyleCategoryImageUrl(
  category: Pick<ColorStyleCategory, 'id' | 'version'>,
): string {
  return `/api/v1/color-style-categories/${category.id}/image?v=${encodeURIComponent(category.version)}`
}

export function getColorStyles(params: {
  categoryId?: number
  keyword?: string
  status?: 'ACTIVE' | 'DISABLED'
  page?: number
  size?: number
} = {}): Promise<PageResult<ColorStyle>> {
  return apiRequest<PageResult<ColorStyle>>({ method: 'GET', url: '/color-styles', params })
}

export function getColorStyle(id: number): Promise<ColorStyle> {
  return apiRequest<ColorStyle>({ method: 'GET', url: `/color-styles/${id}` })
}

export function createColorStyle(payload: CreateColorStylePayload): Promise<ColorStyle> {
  return apiRequest<ColorStyle>({ method: 'POST', url: '/color-styles', data: payload })
}

export function updateColorStyle(id: number, payload: UpdateColorStylePayload): Promise<ColorStyle> {
  return apiRequest<ColorStyle>({ method: 'PUT', url: `/color-styles/${id}`, data: payload })
}

export function addColorStyleAsset(id: number, sortNo: number, file: File): Promise<ColorStyleAsset> {
  const form = new FormData()
  form.append('file', file)
  return apiRequest<ColorStyleAsset>({
    method: 'POST', url: `/color-styles/${id}/assets`, params: { sortNo }, data: form,
  })
}

export function updateColorStyleAsset(
  styleId: number,
  assetId: number,
  payload: Pick<ColorStyleAsset, 'sortNo' | 'status' | 'version'>,
): Promise<ColorStyleAsset> {
  return apiRequest<ColorStyleAsset>({
    method: 'PUT', url: `/color-styles/${styleId}/assets/${assetId}`, data: payload,
  })
}

export function colorStyleAssetUrl(
  asset: Pick<ColorStyleAsset, 'id' | 'colorStyleId' | 'version'>,
): string {
  return `/api/v1/color-styles/${asset.colorStyleId}/assets/${asset.id}/content?v=${encodeURIComponent(asset.version)}`
}
