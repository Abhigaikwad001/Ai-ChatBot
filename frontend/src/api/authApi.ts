import { apiClient, setAuthToken, removeAuthToken, getAuthToken } from './client'
import type { AuthResponse, LoginRequest, RegisterRequest, UserResponse } from '../types/auth'

export const authApi = {
  async register(request: RegisterRequest): Promise<UserResponse> {
    return apiClient<UserResponse>('/auth/register', {
      method: 'POST',
      body: JSON.stringify(request),
    })
  },

  async login(request: LoginRequest): Promise<AuthResponse> {
    const authData = await apiClient<AuthResponse>('/auth/login', {
      method: 'POST',
      body: JSON.stringify(request),
    })
    if (authData?.accessToken) {
      setAuthToken(authData.accessToken)
    }
    return authData
  },

  async getCurrentUser(): Promise<UserResponse> {
    return apiClient<UserResponse>('/auth/me', {
      method: 'GET',
    })
  },

  logout(): void {
    removeAuthToken()
  },

  getStoredToken(): string | null {
    return getAuthToken()
  },
}
