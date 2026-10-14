// /api/v1/admin (admin/web/AdminController) - ADMIN va STAFF.
// Rieng dashboard doanh thu chi ADMIN; STAFF goi vao se an 403 ACCESS_DENIED.

import { api, qs } from '@/lib/api/client'
import type {
  AdminOrderRow,
  DashboardSummary,
  LowStockItem,
  OrderResponse,
  OrderStatus,
  PageResponse,
  RevenueReport,
  TopProduct,
} from '@/types'

export function listOrders(params: {
  status?: OrderStatus
  from?: string
  to?: string
  page?: number
  size?: number
} = {}): Promise<PageResponse<AdminOrderRow>> {
  return api.get<PageResponse<AdminOrderRow>>(`/admin/orders${qs({ ...params })}`)
}

/**
 * Doi trang thai don, di qua bang chuyen trang thai o OrderStateMachine.
 * Chuyen doi khong hop le tra 409 INVALID_STATE_TRANSITION.
 * Cac chuyen doi lam hang quay ve kho tu dong hoan ton kho va hoan luot coupon.
 */
export function updateOrderStatus(
  code: string,
  toStatus: OrderStatus,
  note?: string,
): Promise<OrderResponse> {
  return api.patch<OrderResponse>(`/admin/orders/${encodeURIComponent(code)}/status`, {
    toStatus,
    note,
  })
}

export function lowStock(threshold = 10): Promise<LowStockItem[]> {
  return api.get<LowStockItem[]>(`/admin/inventory/low-stock${qs({ threshold })}`)
}

// ── Bao cao (chi ADMIN) ───────────────────────────────────────────────────

export function dashboardSummary(): Promise<DashboardSummary> {
  return api.get<DashboardSummary>('/admin/dashboard/summary')
}

export function revenueReport(params: {
  groupBy?: 'day' | 'month'
  from?: string
  to?: string
} = {}): Promise<RevenueReport> {
  return api.get<RevenueReport>(`/admin/dashboard/revenue${qs({ ...params })}`)
}

export function topProducts(params: {
  from?: string
  to?: string
  limit?: number
} = {}): Promise<TopProduct[]> {
  return api.get<TopProduct[]>(`/admin/dashboard/top-products${qs({ ...params })}`)
}

/**
 * Chuyen trang thai hop le ke tiep, doi chieu voi OrderStateMachine o backend.
 * Dung de chi hien nut doi duoc - de khach bam roi an 409 la trai nghiem toi.
 */
export const NEXT_STATUSES: Record<OrderStatus, OrderStatus[]> = {
  PENDING_PAYMENT: ['PAID', 'CANCELLED'],
  PAID: ['PACKED', 'CANCELLED', 'REFUNDED'],
  PACKED: ['SHIPPED', 'CANCELLED'],
  SHIPPED: ['DELIVERED'],
  DELIVERED: ['REFUNDED'],
  CANCELLED: [],
  REFUNDED: [],
}
