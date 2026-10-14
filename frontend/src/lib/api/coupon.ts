// /api/v1/coupons (promotion/web/CouponController).

import { api } from '@/lib/api/client'
import type { CouponResponse, CreateCouponRequest, ValidateCouponResponse } from '@/types'

/**
 * Kiem tra ma - CHI de hien thi truoc, khong giu cho.
 * Luot that su bi tru trong transaction dat hang, nen "hop le" o day roi
 * luc dat hang bi 409 COUPON_USED_UP la hanh vi dung, khong phai loi.
 */
export function validateCoupon(code: string, subtotal: number): Promise<ValidateCouponResponse> {
  return api.post<ValidateCouponResponse>('/coupons/validate', { code, subtotal })
}

// ── ADMIN ─────────────────────────────────────────────────────────────────

export function listCoupons(): Promise<CouponResponse[]> {
  return api.get<CouponResponse[]>('/coupons')
}

export function createCoupon(req: CreateCouponRequest): Promise<CouponResponse> {
  return api.post<CouponResponse>('/coupons', req)
}

/** Vo hieu hoa, khong xoa cung - ma da dung nam trong lich su don. */
export function deactivateCoupon(id: number): Promise<void> {
  return api.del<void>(`/coupons/${id}`)
}
