import { describe, it, expect, vi, beforeEach } from 'vitest'
import {
  apiClient,
  ApiError,
  setAuthToken,
  removeAuthToken,
  getAuthToken,
} from '../../api/client'

describe('Centralized API Client', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
    localStorage.clear()
  })

  it('should store and retrieve auth token from localStorage', () => {
    setAuthToken('sample-jwt-token')
    expect(getAuthToken()).toBe('sample-jwt-token')
    removeAuthToken()
    expect(getAuthToken()).toBeNull()
  })

  it('should automatically attach Authorization Bearer header when token is stored', async () => {
    setAuthToken('valid-token-123')

    const mockFetch = vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      headers: new Headers({ 'Content-Type': 'application/json' }),
      json: async () => ({ success: true, data: { id: 'test-user' } }),
    })
    globalThis.fetch = mockFetch

    const result = await apiClient<{ id: string }>('/auth/me')

    expect(result).toEqual({ id: 'test-user' })
    expect(mockFetch).toHaveBeenCalledWith(
      expect.stringContaining('/auth/me'),
      expect.objectContaining({
        headers: expect.any(Headers),
      })
    )

    const sentHeaders: Headers = mockFetch.mock.calls[0][1].headers
    expect(sentHeaders.get('Authorization')).toBe('Bearer valid-token-123')
  })

  it('should handle 401 Unauthorized by removing token and dispatching auth:unauthorized', async () => {
    setAuthToken('expired-token')

    const eventListener = vi.fn()
    window.addEventListener('auth:unauthorized', eventListener)

    globalThis.fetch = vi.fn().mockResolvedValue({
      ok: false,
      status: 401,
      headers: new Headers({ 'Content-Type': 'application/json' }),
      json: async () => ({ success: false, message: 'Invalid or expired authentication token' }),
    })

    await expect(apiClient('/conversations')).rejects.toThrow(ApiError)
    expect(getAuthToken()).toBeNull()
    expect(eventListener).toHaveBeenCalled()

    window.removeEventListener('auth:unauthorized', eventListener)
  })

  it('should extract structured error messages and field validation errors', async () => {
    globalThis.fetch = vi.fn().mockResolvedValue({
      ok: false,
      status: 400,
      headers: new Headers({ 'Content-Type': 'application/json' }),
      json: async () => ({
        success: false,
        message: 'Validation failed',
        data: { email: 'Email is invalid' },
      }),
    })

    try {
      await apiClient('/auth/register', { method: 'POST' })
      expect.unreachable('Should have thrown an ApiError')
    } catch (err) {
      expect(err).toBeInstanceOf(ApiError)
      const apiErr = err as ApiError
      expect(apiErr.status).toBe(400)
      expect(apiErr.message).toBe('Validation failed')
      expect(apiErr.details).toEqual({ email: 'Email is invalid' })
    }
  })
})
