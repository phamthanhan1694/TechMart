// /api/v1/flash-sales (flashsale/web/FlashSaleController).
//
// Duong di cua mot lan mua: rate limit (10/phut/user, 100/phut/IP) -> cua Redis
// (Lua nguyen tu) -> ghi don o DB. Chi nguoi thang o cua Redis moi cham vao DB.

import { api, newIdempotencyKey } from '@/lib/api/client'
import type {
  CreateFlashSaleRequest,
  FlashSalePurchaseRequest,
  FlashSaleResponse,
  OrderResponse,
} from '@/types'

/** So suat con lai doc tu Redis khi phien dang chay, tu DB khi da ket thuc. */
export function activeFlashSales(): Promise<FlashSaleResponse[]> {
  return api.get<FlashSaleResponse[]>('/flash-sales/active')
}

export function upcomingFlashSales(): Promise<FlashSaleResponse[]> {
  return api.get<FlashSaleResponse[]>('/flash-sales/upcoming')
}

/**
 * Mua mot suat. Header `Idempotency-Key` bat buoc.
 *
 * Ma loi dang cho: 409 SOLD_OUT, 409 FLASH_SALE_LIMIT_EXCEEDED,
 * 429 RATE_LIMIT_EXCEEDED, 503 SERVICE_UNAVAILABLE (Redis chet - fail-closed,
 * khong dam bao duoc thi khong ban).
 */
export function purchaseFlashSaleItem(
  itemId: number,
  req: FlashSalePurchaseRequest,
  idempotencyKey: string = newIdempotencyKey(),
): Promise<OrderResponse> {
  return api.post<OrderResponse>(`/flash-sales/items/${itemId}/purchase`, req, { idempotencyKey })
}

// ── ADMIN ─────────────────────────────────────────────────────────────────

export function createFlashSale(req: CreateFlashSaleRequest): Promise<FlashSaleResponse> {
  return api.post<FlashSaleResponse>('/flash-sales', req)
}

/** Nap ton kho vao Redis truoc gio mo ban - chan cache stampede dung giay dau. */
export function warmUpFlashSale(
  id: number,
): Promise<{ flashSaleId: number; warmedUpItems: number; status: string }> {
  return api.post(`/flash-sales/${id}/warmup`)
}

/** Doi soat Redis voi DB sau khi phien ket thuc; lech thi ghi canh bao vao log. */
export function reconcileFlashSale(id: number): Promise<{ status: string }> {
  return api.post(`/flash-sales/${id}/reconcile`)
}
