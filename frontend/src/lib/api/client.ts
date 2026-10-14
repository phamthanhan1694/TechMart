/**
 * Loi truyen tai cua toan bo frontend: fetch + Bearer token + xoay vong refresh token.
 *
 * Mac dinh request di qua rewrites cua Next (`/api/...` -> BACKEND_URL) nen khong dinh CORS.
 * Dat NEXT_PUBLIC_API_BASE_URL=http://localhost:8080 de browser goi thang Spring Boot.
 *
 * Cac module theo domain (catalog.ts, cart.ts, ...) chi dung `apiFetch` o day,
 * khong tu goi fetch - de moi duong di deu di qua cung mot cho xu ly 401 va loi.
 */

import {
  clearAuth,
  getRefreshToken,
  getToken,
  saveTokens,
} from '@/lib/auth'
import type { ApiErrorBody, TokenResponse } from '@/types'

const BASE = (process.env.NEXT_PUBLIC_API_BASE_URL ?? '').replace(/\/$/, '')
const API = `${BASE}/api/v1`

/**
 * Loi tra ve tu API, giu nguyen `code` va `details` cua ApiError.
 * Man hinh nen phan nhanh theo `code`, khong doc `message` - message co the doi.
 */
export class ApiError extends Error {
  readonly status: number
  readonly code: string
  readonly details: unknown
  readonly traceId?: string

  constructor(body: Partial<ApiErrorBody> & { status: number }) {
    super(body.message || 'Yeu cau that bai')
    this.name = 'ApiError'
    this.status = body.status
    this.code = body.code ?? 'UNKNOWN'
    this.details = body.details
    this.traceId = body.traceId
  }
}

/** UUID cho header Idempotency-Key. crypto.randomUUID co o moi browser hien dai. */
export function newIdempotencyKey(): string {
  if (typeof crypto !== 'undefined' && 'randomUUID' in crypto) {
    return crypto.randomUUID()
  }
  return `${Date.now()}-${Math.random().toString(16).slice(2)}-${Math.random().toString(16).slice(2)}`
}

function authHeaders(extra?: HeadersInit): Record<string, string> {
  const token = getToken()
  return {
    'Content-Type': 'application/json',
    ...(extra as Record<string, string>),
    ...(token ? { Authorization: `Bearer ${token}` } : {}),
  }
}

/** Phien het han that su -> xoa phien va dua ve trang dang nhap. */
function handleSessionExpired(): void {
  clearAuth()
  if (typeof window !== 'undefined' && !window.location.pathname.startsWith('/login')) {
    const back = encodeURIComponent(window.location.pathname + window.location.search)
    // URL tuyet doi: gan chuoi tuong doi vao location la nhap nhang.
    window.location.assign(new URL(`/login?next=${back}`, window.location.origin))
  }
}

// ── Xoay vong refresh token (single-flight) ───────────────────────────────
// Nhieu request 401 cung luc chi duoc goi /auth/refresh MOT lan. Goi song song
// se co request dung refresh token vua bi thu hoi, va backend coi do la replay
// -> thu hoi ca chuoi token, dang xuat nguoi dung dang dung binh thuong.
let refreshInFlight: Promise<boolean> | null = null

async function refreshTokens(): Promise<boolean> {
  const refreshToken = getRefreshToken()
  if (!refreshToken) return false

  const res = await fetch(`${API}/auth/refresh`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ refreshToken }),
  })
  if (!res.ok) return false

  const data = (await res.json()) as TokenResponse
  saveTokens(data.accessToken, data.refreshToken)
  return true
}

function refreshOnce(): Promise<boolean> {
  refreshInFlight ??= refreshTokens()
    .catch(() => false)
    .finally(() => {
      // Nha cho lan 401 tiep theo (sau khi access token moi cung het han).
      setTimeout(() => {
        refreshInFlight = null
      }, 0)
    })
  return refreshInFlight
}

async function toApiError(res: Response): Promise<ApiError> {
  const body = (await res.json().catch(() => null)) as ApiErrorBody | null
  return new ApiError({ ...(body ?? {}), status: body?.status ?? res.status })
}

export interface ApiOptions extends RequestInit {
  /** Gui header Idempotency-Key. Bat buoc voi dat hang, thanh toan, mua flash sale. */
  idempotencyKey?: string
  /** Dung noi bo: danh dau request da thu lai sau khi refresh, khong thu lan hai. */
  _retried?: boolean
}

export async function apiFetch<T>(path: string, options: ApiOptions = {}): Promise<T> {
  const { idempotencyKey, _retried, ...init } = options

  const res = await fetch(`${API}${path}`, {
    ...init,
    headers: authHeaders({
      ...(init.headers as Record<string, string>),
      ...(idempotencyKey ? { 'Idempotency-Key': idempotencyKey } : {}),
    }),
  })

  if (res.status === 401) {
    // Chua tung dang nhap (vd form login sai) -> tra loi cua server, khong dieu huong.
    if (!getToken()) throw await toApiError(res)

    // Da tung dang nhap: thu xoay vong token dung mot lan roi phat lai request.
    // Chinh `idempotencyKey` duoc gui lai nguyen ven - backend idempotent nen
    // du request dau da cham DB thi lan hai van tra ve dung ket qua cu.
    if (!_retried && (await refreshOnce())) {
      return apiFetch<T>(path, { ...options, _retried: true })
    }
    handleSessionExpired()
    throw await toApiError(res)
  }

  if (!res.ok) throw await toApiError(res)

  // 204 No Content
  if (res.status === 204) return undefined as T
  return res.json() as Promise<T>
}

/**
 * Tải file cần Bearer token (vd CSV xuất đơn hàng).
 *
 * Không dùng thẻ <a href> được: trình duyệt không gắn header Authorization vào lượt tải đó.
 * Nên phải fetch thành Blob rồi tự kích hoạt tải. Đổi lại cả file nằm trong bộ nhớ trình
 * duyệt một lúc - chấp nhận được vì backend chặn trần 100.000 dòng.
 */
export async function apiDownload(path: string, fallbackName: string, _retried = false): Promise<void> {
  const token = getToken()
  const res = await fetch(`${API}${path}`, {
    headers: token ? { Authorization: `Bearer ${token}` } : {},
  })
  if (res.status === 401 && !_retried && (await refreshOnce())) {
    return apiDownload(path, fallbackName, true)
  }
  if (!res.ok) throw await toApiError(res)

  // Lấy tên file backend đặt (có khoảng ngày); không có thì dùng tên dự phòng.
  const disposition = res.headers.get('Content-Disposition') ?? ''
  const match = /filename="?([^"]+)"?/.exec(disposition)
  const blob = await res.blob()
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = match?.[1] ?? fallbackName
  document.body.appendChild(a)
  a.click()
  a.remove()
  URL.revokeObjectURL(url)
}

// ── Helper cho query string ───────────────────────────────────────────────

type QueryValue = string | number | boolean | null | undefined

/** Bo qua gia tri rong de khong gui `?keyword=&categoryId=` len backend. */
export function qs(params: Record<string, QueryValue>): string {
  const sp = new URLSearchParams()
  for (const [k, v] of Object.entries(params)) {
    if (v === null || v === undefined || v === '') continue
    sp.set(k, String(v))
  }
  const s = sp.toString()
  return s ? `?${s}` : ''
}

// ── Cac verb hay dung ─────────────────────────────────────────────────────

export const api = {
  get: <T>(path: string, options?: ApiOptions) =>
    apiFetch<T>(path, { ...options, method: 'GET' }),

  post: <T>(path: string, body?: unknown, options?: ApiOptions) =>
    apiFetch<T>(path, {
      ...options,
      method: 'POST',
      body: body === undefined ? undefined : JSON.stringify(body),
    }),

  put: <T>(path: string, body?: unknown, options?: ApiOptions) =>
    apiFetch<T>(path, {
      ...options,
      method: 'PUT',
      body: body === undefined ? undefined : JSON.stringify(body),
    }),

  patch: <T>(path: string, body?: unknown, options?: ApiOptions) =>
    apiFetch<T>(path, {
      ...options,
      method: 'PATCH',
      body: body === undefined ? undefined : JSON.stringify(body),
    }),

  del: <T>(path: string, options?: ApiOptions) =>
    apiFetch<T>(path, { ...options, method: 'DELETE' }),
}
