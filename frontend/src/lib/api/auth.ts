// /api/v1/auth - dang ky, dang nhap, xoay vong token (identity/web/AuthController).

import { api } from '@/lib/api/client'
import { getRefreshToken } from '@/lib/auth'
import type { RegisterRequest, TokenResponse, UserResponse } from '@/types'

export function login(email: string, password: string): Promise<TokenResponse> {
  return api.post<TokenResponse>('/auth/login', { email, password })
}

export function register(req: RegisterRequest): Promise<UserResponse> {
  // phone rong -> bo han khoi payload, backend cho phep null nhung khong cho chuoi sai dinh dang.
  return api.post<UserResponse>('/auth/register', {
    ...req,
    phone: req.phone?.trim() ? req.phone.trim() : undefined,
  })
}

export function me(): Promise<UserResponse> {
  return api.get<UserResponse>('/auth/me')
}

/** Thu hoi refresh token o server. Loi o day khong chan viec xoa phien o client. */
export function logout(): Promise<void> {
  const refreshToken = getRefreshToken()
  if (!refreshToken) return Promise.resolve()
  return api.post<void>('/auth/logout', { refreshToken })
}

export function logoutAllDevices(): Promise<void> {
  return api.post<void>('/auth/logout-all')
}
