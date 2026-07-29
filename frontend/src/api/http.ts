import axios, { type AxiosRequestConfig } from 'axios'
import type { ApiResponse } from '@/types/api'

let csrfToken = ''
let csrfLoading: Promise<void> | null = null

const http = axios.create({
  baseURL: '/api/v1',
  timeout: 15_000,
  withCredentials: true,
  headers: {
    'Content-Type': 'application/json',
  },
})

http.interceptors.request.use((config) => {
  const method = config.method?.toUpperCase()
  if (csrfToken && method && !['GET', 'HEAD', 'OPTIONS'].includes(method)) {
    config.headers.set('X-XSRF-TOKEN', csrfToken)
  }
  return config
})

export async function ensureCsrf(): Promise<void> {
  if (csrfToken) return
  if (!csrfLoading) {
    csrfLoading = http
      .get<ApiResponse<{ headerName: string; token: string }>>('/auth/csrf')
      .then((response) => {
        csrfToken = response.data.data.token
      })
      .finally(() => {
        csrfLoading = null
      })
  }
  return csrfLoading
}

export async function apiRequest<T>(config: AxiosRequestConfig): Promise<T> {
  try {
    const response = await http.request<ApiResponse<T>>(config)
    if (response.data.code !== '0') {
      throw new Error(response.data.message)
    }
    return response.data.data
  } catch (error) {
    if (axios.isAxiosError<ApiResponse<unknown>>(error)) {
      const message = error.response?.data?.message ?? (error.code === 'ECONNABORTED' ? '请求超时' : '网络请求失败')
      const requestError = new Error(message) as Error & { status?: number; traceId?: string }
      requestError.status = error.response?.status
      requestError.traceId = error.response?.data?.traceId
      throw requestError
    }
    throw error
  }
}
