import { describe, it, expect, vi, beforeEach } from 'vitest'
import { apiClient, ApiError, getApiBaseUrl } from '../../api/client'

describe('Centralized API Client', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
  })

  it('should return default or environment API base URL', () => {
    const url = getApiBaseUrl()
    expect(url).toBeDefined()
    expect(typeof url).toBe('string')
  })

  it('should successfully execute request and unwrap ApiResponse data', async () => {
    const mockFetch = vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      headers: new Headers({ 'Content-Type': 'application/json' }),
      json: async () => ({ success: true, data: [{ id: 'conv-1', title: 'Test Chat' }] }),
    })
    globalThis.fetch = mockFetch

    const result = await apiClient<{ id: string; title: string }[]>('/conversations')

    expect(result).toEqual([{ id: 'conv-1', title: 'Test Chat' }])
    expect(mockFetch).toHaveBeenCalledWith(
      expect.stringContaining('/conversations'),
      expect.objectContaining({
        headers: expect.any(Headers),
      })
    )

    const sentHeaders: Headers = mockFetch.mock.calls[0][1].headers
    expect(sentHeaders.get('Content-Type')).toBe('application/json')
  })

  it('should handle network connection failure', async () => {
    globalThis.fetch = vi.fn().mockRejectedValue(new Error('Failed to fetch'))

    await expect(apiClient('/conversations')).rejects.toThrow(ApiError)
    await expect(apiClient('/conversations')).rejects.toThrow(/Cannot connect to server/)
  })

  it('should extract structured error messages and validation errors on non-2xx response', async () => {
    globalThis.fetch = vi.fn().mockResolvedValue({
      ok: false,
      status: 400,
      headers: new Headers({ 'Content-Type': 'application/json' }),
      json: async () => ({
        success: false,
        message: 'Validation failed',
        data: { title: 'Title cannot be blank' },
      }),
    })

    try {
      await apiClient('/conversations', { method: 'POST', body: JSON.stringify({}) })
      expect.unreachable('Should have thrown an ApiError')
    } catch (err) {
      expect(err).toBeInstanceOf(ApiError)
      const apiErr = err as ApiError
      expect(apiErr.status).toBe(400)
      expect(apiErr.message).toBe('Validation failed')
      expect(apiErr.details).toEqual({ title: 'Title cannot be blank' })
    }
  })
})
