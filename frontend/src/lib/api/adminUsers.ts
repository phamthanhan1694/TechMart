// /api/v1/admin/users (identity/web/AdminUserController) - chi ADMIN.
// Moi thao tac ghi deu vao audit log o backend.

import { api, qs } from '@/lib/api/client'
import type {
  AdminUserDetail,
  AdminUserRow,
  CreateUserRequest,
  PageResponse,
  Role,
  UserStatus,
} from '@/types'

export function search(params: {
  keyword?: string
  role?: Role
  status?: UserStatus
  /** Vd 'totalSpent,desc'. Backend whitelist: createdAt, totalSpent, paidOrders, lastOrderAt, email. */
  sort?: string
  page?: number
  size?: number
}): Promise<PageResponse<AdminUserRow>> {
  return api.get<PageResponse<AdminUserRow>>(`/admin/users${qs({ ...params })}`)
}

export const detail = (id: number) => api.get<AdminUserDetail>(`/admin/users/${id}`)

export const create = (req: CreateUserRequest) => api.post<AdminUserRow>('/admin/users', req)

/** Khoá thì thu hồi mọi refresh token. 409 CANNOT_MODIFY_SELF / LAST_ADMIN khi vi phạm. */
export const changeStatus = (id: number, status: UserStatus, reason?: string) =>
  api.patch<AdminUserRow>(`/admin/users/${id}/status`, { status, reason })

export const changeRole = (id: number, role: Role) =>
  api.patch<AdminUserRow>(`/admin/users/${id}/role`, { role })

/** Gỡ khoá đăng nhập tạm thời sau nhiều lần gõ sai mật khẩu. */
export const clearLoginLockout = (id: number) => api.del<void>(`/admin/users/${id}/login-lockout`)
