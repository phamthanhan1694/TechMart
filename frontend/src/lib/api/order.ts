// /api/v1/orders (order/web/OrderController).

import { api, newIdempotencyKey, qs } from '@/lib/api/client'
import type {
  OrderResponse,
  OrderStatus,
  OrderSummaryResponse,
  PageResponse,
  PlaceOrderRequest,
  StatusHistoryResponse,
} from '@/types'

/**
 * Dat hang tu phien checkout. Header `Idempotency-Key` la BAT BUOC.
 *
 * Client sinh key MOT lan roi giu nguyen qua moi lan bam lai: khach double-click
 * hay mang chap chon rieu request, backend deu tra ve dung don da tao chu khong tao don thu hai.
 */
export function placeOrder(
  req: PlaceOrderRequest,
  idempotencyKey: string = newIdempotencyKey(),
): Promise<OrderResponse> {
  return api.post<OrderResponse>('/orders', req, { idempotencyKey })
}

export function listMyOrders(
  page = 0,
  size = 20,
): Promise<PageResponse<OrderSummaryResponse>> {
  return api.get<PageResponse<OrderSummaryResponse>>(`/orders${qs({ page, size })}`)
}

/** Don cua nguoi khac tra 404 chu khong 403 - de khong lo ma don nao co that. */
export function getOrder(code: string): Promise<OrderResponse> {
  return api.get<OrderResponse>(`/orders/${encodeURIComponent(code)}`)
}

export function getOrderHistory(code: string): Promise<StatusHistoryResponse[]> {
  return api.get<StatusHistoryResponse[]>(`/orders/${encodeURIComponent(code)}/history`)
}

/** Idempotent: huy mot don da huy tra 200 va khong hoan kho lan hai. */
export function cancelOrder(code: string, reason?: string): Promise<OrderResponse> {
  return api.post<OrderResponse>(`/orders/${encodeURIComponent(code)}/cancel`, { reason })
}

/** Trang thai nao con huy duoc - dung de an nut thay vi de khach bam roi an 409. */
export function isCancellable(status: OrderStatus): boolean {
  return status === 'PENDING_PAYMENT' || status === 'PAID'
}
