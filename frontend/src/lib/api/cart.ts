// /api/v1/cart (cart/web/CartController) - chi CUSTOMER.
// Luu y: them vao gio KHONG giu hang. Hang chi bi giu khi tao phien checkout.

import { api } from '@/lib/api/client'
import type { CartResponse } from '@/types'

export function getCart(): Promise<CartResponse> {
  return api.get<CartResponse>('/cart')
}

/** Variant da co trong gio thi backend cong don so luong. */
export function addToCart(variantId: number, quantity: number): Promise<CartResponse> {
  return api.post<CartResponse>('/cart/items', { variantId, quantity })
}

export function updateCartItem(itemId: number, quantity: number): Promise<CartResponse> {
  return api.put<CartResponse>(`/cart/items/${itemId}`, { quantity })
}

export function removeCartItem(itemId: number): Promise<CartResponse> {
  return api.del<CartResponse>(`/cart/items/${itemId}`)
}

export function clearCart(): Promise<void> {
  return api.del<void>('/cart')
}
