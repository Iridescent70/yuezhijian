import { apiRequest, ensureCsrf } from './http'
import type { CurrentUser } from '@/types/api'

export async function login(username: string, password: string): Promise<CurrentUser> {
  await ensureCsrf()
  return apiRequest<CurrentUser>({ method: 'POST', url: '/auth/login', data: { username, password } })
}

export function getCurrentUser(): Promise<CurrentUser> {
  return apiRequest<CurrentUser>({ method: 'GET', url: '/auth/me' })
}

export async function switchCurrentStore(storeId: number): Promise<CurrentUser> {
  await ensureCsrf()
  return apiRequest<CurrentUser>({ method: 'POST', url: '/auth/current-store', data: { storeId } })
}

export async function logout(): Promise<void> {
  await ensureCsrf()
  await apiRequest<void>({ method: 'POST', url: '/auth/logout' })
}
