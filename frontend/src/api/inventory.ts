import { apiRequest } from './http'
import type {
  CountDetail,
  CountSummary,
  Gift,
  GiftPayload,
  InventoryCountStatus,
  InventoryTransferStatus,
  PageResult,
  StockItem,
  StockLedgerItem,
  TransferDetail,
  TransferSummary,
} from '@/types/api'

export function getGifts(params?: {
  keyword?: string
  status?: string
  page?: number
  size?: number
}): Promise<PageResult<Gift>> {
  return apiRequest<PageResult<Gift>>({ method: 'GET', url: '/gifts', params })
}

export async function getAllGifts(status?: 'ACTIVE' | 'DISABLED'): Promise<Gift[]> {
  const size = 100
  const first = await getGifts({ status, page: 1, size })
  const items = [...first.items]
  const pages = Math.ceil(first.total / size)
  for (let page = 2; page <= pages; page += 1) {
    items.push(...(await getGifts({ status, page, size })).items)
  }
  return items
}

export function getGift(id: number): Promise<Gift> {
  return apiRequest<Gift>({ method: 'GET', url: `/gifts/${id}` })
}

export function createGift(payload: GiftPayload): Promise<Gift> {
  return apiRequest<Gift>({ method: 'POST', url: '/gifts', data: payload })
}

export function updateGift(id: number, payload: Omit<GiftPayload, 'code'> & {
  status: 'ACTIVE' | 'DISABLED'
  version: string
}): Promise<Gift> {
  return apiRequest<Gift>({ method: 'PUT', url: `/gifts/${id}`, data: payload })
}

export function getStocks(params: {
  storeId?: number
  keyword?: string
  lowStock?: boolean
  page?: number
  size?: number
}): Promise<PageResult<StockItem>> {
  return apiRequest<PageResult<StockItem>>({ method: 'GET', url: '/inventories', params })
}

export function getStockLedgers(
  storeId: number,
  giftId: number,
  params?: { page?: number; size?: number },
): Promise<PageResult<StockLedgerItem>> {
  return apiRequest<PageResult<StockLedgerItem>>({
    method: 'GET', url: `/inventories/${storeId}/gifts/${giftId}/ledgers`, params,
  })
}

export function getTransfers(params?: {
  storeId?: number
  keyword?: string
  status?: InventoryTransferStatus
  page?: number
  size?: number
}): Promise<PageResult<TransferSummary>> {
  return apiRequest<PageResult<TransferSummary>>({ method: 'GET', url: '/inventory-transfers', params })
}

export function getTransfer(id: number): Promise<TransferDetail> {
  return apiRequest<TransferDetail>({ method: 'GET', url: `/inventory-transfers/${id}` })
}

export function createTransfer(payload: {
  sourceStoreId: number
  targetStoreId: number
  transferDate: string
  remarks?: string
  lines: Array<{ giftId: number; quantity: number; note?: string }>
  idempotencyKey: string
}): Promise<TransferDetail> {
  return apiRequest<TransferDetail>({ method: 'POST', url: '/inventory-transfers', data: payload })
}

export function confirmTransfer(id: number, version: string, reason?: string): Promise<TransferDetail> {
  return apiRequest<TransferDetail>({
    method: 'POST', url: `/inventory-transfers/${id}/confirm`, data: { version, reason },
  })
}

export function voidTransfer(id: number, version: string, reason: string): Promise<TransferDetail> {
  return apiRequest<TransferDetail>({
    method: 'POST', url: `/inventory-transfers/${id}/void`, data: { version, reason },
  })
}

export function reverseTransfer(id: number, version: string, reason: string): Promise<TransferDetail> {
  return apiRequest<TransferDetail>({
    method: 'POST', url: `/inventory-transfers/${id}/reverse`, data: { version, reason },
  })
}

export function getCounts(params?: {
  storeId?: number
  keyword?: string
  status?: InventoryCountStatus
  page?: number
  size?: number
}): Promise<PageResult<CountSummary>> {
  return apiRequest<PageResult<CountSummary>>({ method: 'GET', url: '/inventory-counts', params })
}

export function getCount(id: number): Promise<CountDetail> {
  return apiRequest<CountDetail>({ method: 'GET', url: `/inventory-counts/${id}` })
}

export function createCount(payload: {
  storeId: number
  name: string
  countDate: string
  giftIds: number[]
  remarks?: string
  idempotencyKey: string
}): Promise<CountDetail> {
  return apiRequest<CountDetail>({ method: 'POST', url: '/inventory-counts', data: payload })
}

export function saveCountLines(
  id: number,
  version: string,
  lines: Array<{ lineId: number; actualQuantity: number }>,
): Promise<CountDetail> {
  return apiRequest<CountDetail>({ method: 'PUT', url: `/inventory-counts/${id}/lines`, data: { version, lines } })
}

export function confirmCount(id: number, version: string, reason?: string): Promise<CountDetail> {
  return apiRequest<CountDetail>({
    method: 'POST', url: `/inventory-counts/${id}/confirm`, data: { version, reason },
  })
}

export function voidCount(id: number, version: string, reason: string): Promise<CountDetail> {
  return apiRequest<CountDetail>({
    method: 'POST', url: `/inventory-counts/${id}/void`, data: { version, reason },
  })
}
