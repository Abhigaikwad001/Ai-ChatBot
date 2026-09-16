import type { ApiResponse } from '../types/api'

export class ApiError extends Error {
  public status: number
  public details?: unknown

  constructor(status: number, message: string, details?: unknown) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.details = details
  }
}

export const getApiBaseUrl = (): string => {
  return import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api/v1'
}

export async function apiClient<T>(
  endpoint: string,
  options: RequestInit = {}
): Promise<T> {
  const baseUrl = getApiBaseUrl()
  const cleanEndpoint = endpoint.startsWith('/') ? endpoint : `/${endpoint}`
  const url = `${baseUrl}${cleanEndpoint}`

  const headers = new Headers(options.headers || {})

  if (!headers.has('Content-Type') && !(options.body instanceof FormData)) {
    headers.set('Content-Type', 'application/json')
  }

  let response: Response
  try {
    response = await fetch(url, {
      ...options,
      headers,
    })
  } catch (err: unknown) {
    const message = err instanceof Error ? err.message : 'Network error'
    throw new ApiError(0, `Cannot connect to server: ${message}`)
  }

  let responseBody: unknown = null
  const contentType = response.headers.get('Content-Type') || ''
  if (contentType.includes('application/json')) {
    try {
      responseBody = await response.json()
    } catch {
      responseBody = null
    }
  } else {
    responseBody = await response.text()
  }

  if (!response.ok) {
    let errorMessage = `Request failed with status ${response.status}`
    let details: unknown = null

    if (responseBody && typeof responseBody === 'object') {
      const apiResp = responseBody as Partial<ApiResponse<unknown>>
      if (apiResp.message) {
        errorMessage = apiResp.message
      }
      if (apiResp.data !== undefined) {
        details = apiResp.data
      }
    } else if (typeof responseBody === 'string' && responseBody.trim()) {
      errorMessage = responseBody
    }

    throw new ApiError(response.status, errorMessage, details)
  }

  if (responseBody && typeof responseBody === 'object' && 'success' in (responseBody as object)) {
    const apiResp = responseBody as ApiResponse<T>
    return apiResp.data
  }

  return responseBody as T
}
