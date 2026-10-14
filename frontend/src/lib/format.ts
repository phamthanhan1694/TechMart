// Dinh dang hien thi. Gom mot cho de moi man hinh hien tien va ngay giong nhau.

const VND = new Intl.NumberFormat('vi-VN', {
  style: 'currency',
  currency: 'VND',
  maximumFractionDigits: 0,
})

const NUM = new Intl.NumberFormat('vi-VN')

/** 25000000 -> "25.000.000 ₫". Backend tra BigDecimal nen co the la string. */
export function formatPrice(value: number | string | null | undefined): string {
  const n = typeof value === 'string' ? Number(value) : value
  if (n === null || n === undefined || Number.isNaN(n)) return '—'
  return VND.format(n)
}

/** Rut gon cho the san pham: 25.990.000 -> "25,99 tr". */
export function formatPriceShort(value: number | string | null | undefined): string {
  const n = typeof value === 'string' ? Number(value) : value
  if (n === null || n === undefined || Number.isNaN(n)) return '—'
  if (n >= 1_000_000) return `${(n / 1_000_000).toFixed(2).replace('.', ',')} tr`
  if (n >= 1_000) return `${Math.round(n / 1_000)}k`
  return NUM.format(n)
}

/**
 * Rút gọn cho số tiền lớn ở trang thống kê: 18.765.640.000 -> "18,77 tỉ".
 * formatPriceShort chỉ tới "tr" nên doanh thu tháng ra "18765,64 tr" - khó đọc.
 */
export function formatMoneyCompact(value: number | string | null | undefined): string {
  const n = typeof value === 'string' ? Number(value) : value
  if (n === null || n === undefined || Number.isNaN(n)) return '—'
  const abs = Math.abs(n)
  const fmt = (x: number, unit: string) => `${x.toFixed(x >= 100 ? 0 : x >= 10 ? 1 : 2).replace('.', ',')} ${unit}`
  if (abs >= 1_000_000_000) return fmt(n / 1_000_000_000, 'tỉ')
  if (abs >= 1_000_000) return fmt(n / 1_000_000, 'tr')
  if (abs >= 1_000) return `${Math.round(n / 1_000)}k`
  return NUM.format(n)
}

/** "yyyy-MM-dd" -> Date theo giờ máy, không qua UTC (new Date("2026-09-01") là nửa đêm UTC). */
export function parseLocalDate(isoDate: string): Date {
  const [y, m, d] = isoDate.split('-').map(Number)
  return new Date(y, (m ?? 1) - 1, d ?? 1)
}

export function formatNumber(value: number | null | undefined): string {
  if (value === null || value === undefined || Number.isNaN(value)) return '—'
  return NUM.format(value)
}

export function formatPercent(value: number | null | undefined, digits = 0): string {
  if (value === null || value === undefined || Number.isNaN(value)) return '—'
  return `${value.toFixed(digits).replace('.', ',')}%`
}

/** ISO Instant -> "01/09/2026 14:23". */
export function formatDateTime(iso: string | null | undefined): string {
  if (!iso) return '—'
  const d = new Date(iso)
  if (Number.isNaN(d.getTime())) return '—'
  return d.toLocaleString('vi-VN', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  })
}

export function formatDate(iso: string | null | undefined): string {
  if (!iso) return '—'
  const d = new Date(iso)
  if (Number.isNaN(d.getTime())) return '—'
  return d.toLocaleDateString('vi-VN', { day: '2-digit', month: '2-digit', year: 'numeric' })
}

/** yyyy-MM-dd cho input[type=date] va query param cua backend (@DateTimeFormat ISO.DATE). */
export function toDateInput(d: Date): string {
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}

/** yyyy-MM-ddTHH:mm cho input[type=datetime-local], theo gio may cua nguoi dung. */
export function toDateTimeLocalInput(d: Date): string {
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`
}

/** 605 -> "10:05". Dung cho dong ho dem nguoc giu hang va flash sale. */
export function formatCountdown(totalSeconds: number): string {
  const s = Math.max(0, Math.floor(totalSeconds))
  const h = Math.floor(s / 3600)
  const m = Math.floor((s % 3600) / 60)
  const sec = s % 60
  const pad = (n: number) => String(n).padStart(2, '0')
  return h > 0 ? `${h}:${pad(m)}:${pad(sec)}` : `${pad(m)}:${pad(sec)}`
}

export function formatRelative(iso: string | null | undefined): string {
  if (!iso) return '—'
  const then = new Date(iso).getTime()
  if (Number.isNaN(then)) return '—'
  const diff = Math.floor((Date.now() - then) / 1000)
  if (diff < 60) return 'vừa xong'
  if (diff < 3600) return `${Math.floor(diff / 60)} phút trước`
  if (diff < 86400) return `${Math.floor(diff / 3600)} giờ trước`
  if (diff < 2592000) return `${Math.floor(diff / 86400)} ngày trước`
  return formatDate(iso)
}

/** Ten hien thi cua key spec ky thuat. */
const SPEC_LABELS: Record<string, string> = {
  ram: 'RAM',
  storage: 'Bộ nhớ',
  cpu: 'CPU',
  chipset: 'Chipset',
  gpu: 'GPU',
  screen: 'Màn hình',
  os: 'Hệ điều hành',
  camera: 'Camera',
  battery: 'Pin',
  refreshRate: 'Tần số quét',
  weight: 'Trọng lượng',
  releaseYear: 'Năm ra mắt',
}

export function specLabel(key: string): string {
  return SPEC_LABELS[key] ?? key
}

export function specValue(value: unknown): string {
  if (value === null || value === undefined || value === '') return '—'
  if (typeof value === 'boolean') return value ? 'Có' : 'Không'
  return String(value)
}
