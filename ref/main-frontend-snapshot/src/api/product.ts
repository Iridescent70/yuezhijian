import { apiRequest } from './http'
import type {
  AsyncJobItem,
  CategoryOption,
  CreateProductPayload,
  CreatedResource,
  ProductDetail,
  ProductBatchResult,
  ProductSummary,
  UnitOption,
  UpdateProductPayload,
} from '@/types/api'

export function getProductCategories(): Promise<CategoryOption[]> {
  return apiRequest<CategoryOption[]>({ method: 'GET', url: '/item-categories', params: { type: 'PRODUCT' } })
}

export function getUnits(): Promise<UnitOption[]> {
  return apiRequest<UnitOption[]>({ method: 'GET', url: '/units' })
}

export function getProducts(params?: {
  storeId?: number
  categoryId?: number
  saleStatus?: string
  keyword?: string
}): Promise<ProductSummary[]> {
  return apiRequest<ProductSummary[]>({ method: 'GET', url: '/products', params })
}

export function getProduct(id: number): Promise<ProductDetail> {
  return apiRequest<ProductDetail>({ method: 'GET', url: `/products/${id}` })
}

export function createProduct(payload: CreateProductPayload): Promise<CreatedResource> {
  return apiRequest<CreatedResource>({ method: 'POST', url: '/products', data: payload })
}

export function updateProduct(id: number, payload: UpdateProductPayload): Promise<ProductDetail> {
  return apiRequest<ProductDetail>({ method: 'PUT', url: `/products/${id}`, data: payload })
}

export function importProducts(file: File): Promise<AsyncJobItem> {
  const data = new FormData()
  data.append('file', file)
  return apiRequest<AsyncJobItem>({ method: 'POST', url: '/products/import', data })
}

export function batchProductSaleStatus(payload: {
  productIds: number[]
  saleStatus: 'ON_SALE' | 'OFF_SALE'
}): Promise<ProductBatchResult> {
  return apiRequest<ProductBatchResult>({ method: 'POST', url: '/products/batch-status', data: payload })
}
