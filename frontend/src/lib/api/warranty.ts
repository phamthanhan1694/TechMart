// /api/v1/serials, /warranty-requests (warranty/web/WarrantyController).

import { api, qs } from '@/lib/api/client'
import type {
  BulkSerialResponse,
  PageResponse,
  SerialResponse,
  WarrantyLookupResponse,
  WarrantyRequestResponse,
  WarrantyStatus,
} from '@/types'

/**
 * Tra cuu bao hanh - cong khai, khong can dang nhap.
 * Thoi han tinh tu luc XUAT KHO (don chuyen PACKED), khong phai tu luc nhap kho.
 */
export function lookupWarranty(serialNo: string): Promise<WarrantyLookupResponse> {
  return api.get<WarrantyLookupResponse>(
    `/serials/${encodeURIComponent(serialNo)}/warranty`,
  )
}

/**
 * Tao yeu cau bao hanh. Backend kiem quyen so huu qua chuoi
 * serial -> order_item -> order -> user; serial cua nguoi khac tra 404.
 * Mot serial chi co mot yeu cau dang mo tai mot thoi diem.
 */
export function createWarrantyRequest(
  serialNo: string,
  reason: string,
): Promise<WarrantyRequestResponse> {
  return api.post<WarrantyRequestResponse>('/warranty-requests', { serialNo, reason })
}

/** Khach chi thay yeu cau cua minh; STAFF/ADMIN thay tat ca. */
export function listWarrantyRequests(
  status?: WarrantyStatus,
  page = 0,
  size = 20,
): Promise<PageResponse<WarrantyRequestResponse>> {
  return api.get<PageResponse<WarrantyRequestResponse>>(
    `/warranty-requests${qs({ status, page, size })}`,
  )
}

export function getWarrantyRequest(id: number): Promise<WarrantyRequestResponse> {
  return api.get<WarrantyRequestResponse>(`/warranty-requests/${id}`)
}

/**
 * [STAFF/ADMIN] REQUESTED -> APPROVED / REJECTED, APPROVED -> COMPLETED.
 * Chuyen doi khac tra 409 INVALID_STATE_TRANSITION.
 */
export function resolveWarrantyRequest(
  id: number,
  toStatus: WarrantyStatus,
  resolution?: string,
): Promise<WarrantyRequestResponse> {
  return api.patch<WarrantyRequestResponse>(`/warranty-requests/${id}`, { toStatus, resolution })
}

// ── STAFF/ADMIN: ton kho theo serial ──────────────────────────────────────

/** Serial da ton tai bi bo qua va liet ke lai, khong lam hong ca lo. */
export function importSerials(
  variantId: number,
  serialNumbers: string[],
  warrantyMonths?: number,
): Promise<BulkSerialResponse> {
  return api.post<BulkSerialResponse>('/admin/serials/bulk', {
    variantId,
    serialNumbers,
    warrantyMonths,
  })
}

export function serialsOfOrderItem(orderItemId: number): Promise<SerialResponse[]> {
  return api.get<SerialResponse[]>(`/admin/order-items/${orderItemId}/serials`)
}
