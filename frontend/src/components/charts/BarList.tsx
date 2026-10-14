'use client'

export interface BarRow {
  key: string
  label: string
  value: number
  /** Chữ hiện ở đầu thanh, vd "1,2 tỉ · 31%". */
  display: string
  /** Dòng phụ nhỏ dưới nhãn, vd "42 đơn · 57 máy". */
  sub?: string
  /** Hàng gộp "Khác": tô xám để không bị đọc như một hạng mục thật. */
  muted?: boolean
}

interface Props {
  rows: BarRow[]
  /** Mặc định lấy giá trị lớn nhất; phễu truyền giá trị bước đầu để thanh là % của 100%. */
  max?: number
  emptyText?: string
}

/**
 * Thanh ngang xếp hạng - một màu duy nhất.
 *
 * Danh mục, thương hiệu, tỉnh thành không có thứ tự tự nhiên, nên KHÔNG tô đậm dần theo
 * độ lớn: chiều dài thanh đã nói độ lớn rồi, tô thêm màu là mã hoá hai lần cùng một thông
 * tin. Thanh ngang thay vì cột đứng vì nhãn tiếng Việt dài ("Điện thoại / Tầm trung")
 * đọc ngang được, xoay 45° thì không.
 *
 * Số nằm ở đầu thanh, bằng màu chữ - không bao giờ bằng màu dữ liệu. Mỗi dòng đã có số
 * nên không cần tooltip để đọc giá trị.
 */
export default function BarList({ rows, max, emptyText = 'Chưa có dữ liệu trong khoảng này.' }: Props) {
  if (rows.length === 0) {
    return <p className="text-sm text-slate-500 dark:text-slate-400 text-center py-8">{emptyText}</p>
  }
  const top = max ?? Math.max(...rows.map((r) => r.value), 1)

  return (
    <ul className="viz flex flex-col gap-3">
      {rows.map((r) => {
        const pct = top > 0 ? Math.max((r.value / top) * 100, r.value > 0 ? 0.8 : 0) : 0
        return (
          <li key={r.key} className="group">
            <div className="flex items-baseline justify-between gap-3 mb-1">
              <div className="min-w-0">
                <p className="text-sm font-semibold text-slate-700 dark:text-slate-200 truncate">{r.label}</p>
                {r.sub && <p className="text-2xs text-slate-500 dark:text-slate-400">{r.sub}</p>}
              </div>
              <p className="text-2xs font-bold text-slate-700 dark:text-slate-200 tabular-nums shrink-0">
                {r.display}
              </p>
            </div>
            {/* Rãnh nền nhạt để thanh ngắn vẫn đọc được là "ngắn so với cả khung" */}
            <div className="h-2.5 rounded-full" style={{ background: 'var(--viz-empty)' }}>
              <div
                className="h-full rounded-full transition-[width,opacity] group-hover:opacity-80"
                style={{ width: `${pct}%`, background: r.muted ? 'var(--viz-prev)' : 'var(--viz-1)' }}
              />
            </div>
          </li>
        )
      })}
    </ul>
  )
}
