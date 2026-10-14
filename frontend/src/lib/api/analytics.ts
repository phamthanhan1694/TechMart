// /api/v1/admin/analytics (analytics/AnalyticsController) - chi ADMIN.
// Moi khoang ngay tinh theo gio Viet Nam o backend; from/to la yyyy-MM-dd, tinh ca hai dau.

import { api, apiDownload, qs } from '@/lib/api/client'
import type {
  AnalyticsOverview,
  Breakdown,
  BreakdownDimension,
  CouponStats,
  CustomerStats,
  Funnel,
  Heatmap,
  InventoryStats,
  OrderStatus,
  PaymentMethodStats,
  ReviewStats,
  SeriesUnit,
  TimeSeries,
} from '@/types'

export interface RangeParams {
  from?: string
  to?: string
}

const BASE = '/admin/analytics'

export const overview = (r: RangeParams) =>
  api.get<AnalyticsOverview>(`${BASE}/overview${qs({ ...r })}`)

export const timeseries = (r: RangeParams & { groupBy: SeriesUnit }) =>
  api.get<TimeSeries>(`${BASE}/timeseries${qs({ ...r })}`)

export const breakdown = (r: RangeParams & { dimension: BreakdownDimension; limit?: number }) =>
  api.get<Breakdown>(`${BASE}/breakdown${qs({ ...r })}`)

export const funnel = (r: RangeParams) => api.get<Funnel>(`${BASE}/funnel${qs({ ...r })}`)

export const heatmap = (r: RangeParams) => api.get<Heatmap>(`${BASE}/heatmap${qs({ ...r })}`)

export const customers = (r: RangeParams & { limit?: number }) =>
  api.get<CustomerStats>(`${BASE}/customers${qs({ ...r })}`)

export const payments = (r: RangeParams) =>
  api.get<PaymentMethodStats[]>(`${BASE}/payments${qs({ ...r })}`)

export const coupons = (r: RangeParams) => api.get<CouponStats>(`${BASE}/coupons${qs({ ...r })}`)

/** Ảnh chụp tồn kho hiện tại - không nhận khoảng ngày. */
export const inventory = (p: { lowThreshold?: number; slowDays?: number } = {}) =>
  api.get<InventoryStats>(`${BASE}/inventory${qs({ ...p })}`)

export const reviews = (r: RangeParams) => api.get<ReviewStats>(`${BASE}/reviews${qs({ ...r })}`)

/** Quá 100.000 dòng backend trả 422 EXPORT_TOO_LARGE trước khi gửi byte nào. */
export const exportOrdersCsv = (r: RangeParams & { status?: OrderStatus }) =>
  apiDownload(`${BASE}/export/orders.csv${qs({ ...r })}`, 'don-hang.csv')
