// Yeu thich (/api/v1/wishlist), thong bao (/api/v1/notifications), audit log (/api/v1/admin/audit-logs).

import { api, qs } from '@/lib/api/client'
import type {
  AuditAction,
  AuditLogEntry,
  NotificationItem,
  PageResponse,
  WishlistItem,
} from '@/types'

// ── Yêu thích (CUSTOMER). PUT/DELETE idempotent: gọi lại không sinh lỗi. ──

export const wishlist = () => api.get<WishlistItem[]>('/wishlist')
export const wishlistIds = () => api.get<number[]>('/wishlist/ids')
export const addToWishlist = (productId: number) => api.put<void>(`/wishlist/${productId}`)
export const removeFromWishlist = (productId: number) => api.del<void>(`/wishlist/${productId}`)

// ── Thông báo. Client polling unread-count - xem ADR-0013. ────────────────

export const notifications = (page = 0, size = 10) =>
  api.get<PageResponse<NotificationItem>>(`/notifications${qs({ page, size })}`)

export const unreadCount = () => api.get<{ count: number }>('/notifications/unread-count')
export const markRead = (id: number) => api.post<void>(`/notifications/${id}/read`)
export const markAllRead = () => api.post<{ updated: number }>('/notifications/read-all')

// ── Audit log (ADMIN) ─────────────────────────────────────────────────────

export const auditLogs = (params: {
  action?: AuditAction
  targetType?: string
  targetId?: string
  from?: string
  to?: string
  page?: number
  size?: number
}) => api.get<PageResponse<AuditLogEntry>>(`/admin/audit-logs${qs({ ...params })}`)
