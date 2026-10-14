// /api/v1/checkout (inventory/web/CheckoutController) - giu hang 10 phut.
//
// Tao phien -> `availableQuantity` nguoi khac thay giam ngay, nhung `stockQuantity`
// chua doi. Het 10 phut ma chua POST /orders thi job cua backend tu nha hang ra.

import { api } from '@/lib/api/client'
import type { CheckoutSessionResponse } from '@/types'

/**
 * Giu toan bo gio hang.
 * Thieu hang -> 409 INSUFFICIENT_STOCK, `details` liet ke tung variant thieu bao nhieu
 * (kieu StockShortage[]) de man hinh chi ra dung dong nao co van de.
 */
export function createCheckoutSession(): Promise<CheckoutSessionResponse> {
  return api.post<CheckoutSessionResponse>('/checkout/sessions')
}

export function getCheckoutSession(sessionId: string): Promise<CheckoutSessionResponse> {
  return api.get<CheckoutSessionResponse>(`/checkout/sessions/${encodeURIComponent(sessionId)}`)
}

/** Nha hang ngay, khong cho het han. */
export function cancelCheckoutSession(sessionId: string): Promise<void> {
  return api.del<void>(`/checkout/sessions/${encodeURIComponent(sessionId)}`)
}
