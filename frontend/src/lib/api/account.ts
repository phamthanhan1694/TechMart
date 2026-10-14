// /api/v1/account (identity/web/AccountController, address/web/AddressController).

import { api } from '@/lib/api/client'
import type { AddressRequest, AddressResponse, UserResponse } from '@/types'

export const updateProfile = (fullName: string, phone?: string) =>
  api.put<UserResponse>('/account/profile', { fullName, phone: phone?.trim() || undefined })

/**
 * Đổi mật khẩu. Sai mật khẩu hiện tại trả 422 CURRENT_PASSWORD_INCORRECT (không phải 401,
 * để client.ts không hiểu nhầm là hết phiên). Thành công thì MỌI phiên bị thu hồi.
 */
export const changePassword = (currentPassword: string, newPassword: string) =>
  api.post<void>('/account/password', { currentPassword, newPassword })

// ── Sổ địa chỉ (chỉ CUSTOMER) ─────────────────────────────────────────────

export const listAddresses = () => api.get<AddressResponse[]>('/account/addresses')

export const createAddress = (req: AddressRequest) =>
  api.post<AddressResponse>('/account/addresses', req)

export const updateAddress = (id: number, req: AddressRequest) =>
  api.put<AddressResponse>(`/account/addresses/${id}`, req)

export const setDefaultAddress = (id: number) =>
  api.post<AddressResponse>(`/account/addresses/${id}/default`)

export const deleteAddress = (id: number) => api.del<void>(`/account/addresses/${id}`)
