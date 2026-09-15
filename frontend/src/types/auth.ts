export type UserRole = 'ROLE_USER' | 'ROLE_ADMIN' | 'ROLE_SERVICE_APP'
export type UserStatus = 'ACTIVE' | 'SUSPENDED' | 'DEACTIVATED'

export interface UserResponse {
  id: string
  email: string
  fullName: string
  role: UserRole
  status: UserStatus
  createdAt: string
  updatedAt: string
}

export interface AuthResponse {
  accessToken: string
  tokenType: string
  expiresInMs: number
  user: UserResponse
}

export interface LoginRequest {
  email: string
  password: string
}

export interface RegisterRequest {
  email: string
  password: string
  fullName: string
}
