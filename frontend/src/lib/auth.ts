// Luu phien dang nhap trong localStorage.
//
// Backend cap MOT CAP token (identity/service/AuthService):
//   - accessToken  TTL 15 phut, gui kem moi request
//   - refreshToken TTL 7 ngay, XOAY VONG - moi lan /auth/refresh la token cu bi thu hoi ngay.
//
// Vi refresh token xoay vong, hai tab cung goi refresh mot luc se co mot tab dung
// token da bi thu hoi -> backend thu hoi CA CHUOI token cua user (chong replay).
// Vi vay client.ts gom moi lan refresh vao mot promise duy nhat (single-flight).

import type { Role, UserResponse } from '@/types'

export const ACCESS_TOKEN_KEY = 'techmart_access_token'
export const REFRESH_TOKEN_KEY = 'techmart_refresh_token'
export const USER_KEY = 'techmart_auth_user'

export type AuthUser = UserResponse

export function saveAuth(accessToken: string, refreshToken: string, user: AuthUser): void {
  localStorage.setItem(ACCESS_TOKEN_KEY, accessToken)
  localStorage.setItem(REFRESH_TOKEN_KEY, refreshToken)
  localStorage.setItem(USER_KEY, JSON.stringify(user))
}

/** Cap nhat rieng cap token sau khi xoay vong, giu nguyen thong tin user. */
export function saveTokens(accessToken: string, refreshToken: string): void {
  localStorage.setItem(ACCESS_TOKEN_KEY, accessToken)
  localStorage.setItem(REFRESH_TOKEN_KEY, refreshToken)
}

export function getToken(): string | null {
  if (typeof window === 'undefined') return null
  return localStorage.getItem(ACCESS_TOKEN_KEY)
}

export function getRefreshToken(): string | null {
  if (typeof window === 'undefined') return null
  return localStorage.getItem(REFRESH_TOKEN_KEY)
}

export function getUser(): AuthUser | null {
  if (typeof window === 'undefined') return null
  try {
    const s = localStorage.getItem(USER_KEY)
    return s ? (JSON.parse(s) as AuthUser) : null
  } catch {
    return null
  }
}

export function saveUser(user: AuthUser): void {
  localStorage.setItem(USER_KEY, JSON.stringify(user))
}

export function clearAuth(): void {
  localStorage.removeItem(ACCESS_TOKEN_KEY)
  localStorage.removeItem(REFRESH_TOKEN_KEY)
  localStorage.removeItem(USER_KEY)
}

/** STAFF va ADMIN deu vao duoc khu quan tri; rieng bao cao doanh thu chi ADMIN. */
export function isStaff(user: AuthUser | null): boolean {
  return user?.role === 'ADMIN' || user?.role === 'STAFF'
}

export function hasRole(user: AuthUser | null, ...roles: Role[]): boolean {
  return user != null && roles.includes(user.role)
}
