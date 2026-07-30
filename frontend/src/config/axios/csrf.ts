import axios from 'axios'
import { config } from './config'

interface ApiResponse<T> {
  code: string | number
  message?: string
  data: T
}

interface CsrfToken {
  headerName: string
  token: string
}

let cachedToken: CsrfToken | undefined
let loadingToken: Promise<CsrfToken> | undefined

export const getCsrfToken = async (): Promise<CsrfToken> => {
  if (cachedToken) return cachedToken
  if (!loadingToken) {
    loadingToken = axios
      .get<ApiResponse<CsrfToken>>(`${config.base_url}/auth/csrf`, {
        withCredentials: true
      })
      .then((response) => {
        if (!['0', '200'].includes(String(response.data.code))) {
          throw new Error(response.data.message || '获取 CSRF Token 失败')
        }
        cachedToken = response.data.data
        return cachedToken
      })
      .finally(() => {
        loadingToken = undefined
      })
  }
  return loadingToken
}

export const resetCsrfToken = () => {
  cachedToken = undefined
  loadingToken = undefined
}
