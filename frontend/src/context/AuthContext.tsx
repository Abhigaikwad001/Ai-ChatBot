import React, { createContext, useContext, useEffect, useState, useCallback } from 'react'
import { authApi } from '../api/authApi'
import type { LoginRequest, RegisterRequest, UserResponse } from '../types/auth'

interface AuthContextType {
  user: UserResponse | null
  token: string | null
  isAuthenticated: boolean
  isLoading: boolean
  error: string | null
  login: (credentials: LoginRequest) => Promise<void>
  register: (data: RegisterRequest) => Promise<void>
  logout: () => void
  clearError: () => void
}

const AuthContext = createContext<AuthContextType | undefined>(undefined)

export const AuthProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [user, setUser] = useState<UserResponse | null>(null)
  const [token, setToken] = useState<string | null>(() => authApi.getStoredToken())
  const [isLoading, setIsLoading] = useState<boolean>(true)
  const [error, setError] = useState<string | null>(null)

  const logout = useCallback(() => {
    authApi.logout()
    setToken(null)
    setUser(null)
  }, [])

  const loadUserProfile = useCallback(async () => {
    const currentToken = authApi.getStoredToken()
    if (!currentToken) {
      setIsLoading(false)
      return
    }

    try {
      setIsLoading(true)
      const profile = await authApi.getCurrentUser()
      setUser(profile)
    } catch {
      // Stored token is invalid or expired
      logout()
    } finally {
      setIsLoading(false)
    }
  }, [logout])

  useEffect(() => {
    loadUserProfile()

    const handleUnauthorized = () => {
      logout()
      setError('Your session has expired. Please log in again.')
    }

    window.addEventListener('auth:unauthorized', handleUnauthorized)
    return () => {
      window.removeEventListener('auth:unauthorized', handleUnauthorized)
    }
  }, [loadUserProfile, logout])

  const login = async (credentials: LoginRequest) => {
    setError(null)
    setIsLoading(true)
    try {
      const resp = await authApi.login(credentials)
      setToken(resp.accessToken)
      setUser(resp.user)
    } catch (err: unknown) {
      const message = err instanceof Error ? err.message : 'Login failed'
      setError(message)
      throw err
    } finally {
      setIsLoading(false)
    }
  }

  const register = async (data: RegisterRequest) => {
    setError(null)
    setIsLoading(true)
    try {
      await authApi.register(data)
      // Auto-login upon successful registration
      const loginResp = await authApi.login({
        email: data.email,
        password: data.password,
      })
      setToken(loginResp.accessToken)
      setUser(loginResp.user)
    } catch (err: unknown) {
      const message = err instanceof Error ? err.message : 'Registration failed'
      setError(message)
      throw err
    } finally {
      setIsLoading(false)
    }
  }

  const clearError = () => setError(null)

  return (
    <AuthContext.Provider
      value={{
        user,
        token,
        isAuthenticated: !!token && !!user,
        isLoading,
        error,
        login,
        register,
        logout,
        clearError,
      }}
    >
      {children}
    </AuthContext.Provider>
  )
}

export const useAuth = (): AuthContextType => {
  const context = useContext(AuthContext)
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider')
  }
  return context
}
