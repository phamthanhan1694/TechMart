'use client'

import { ChevronLeft, ChevronRight } from 'lucide-react'
import { formatNumber } from '@/lib/format'

interface Props {
  /** Trang hiện tại, đánh số từ 0 giống backend. */
  page: number
  totalPages: number
  totalElements: number
  onChange: (page: number) => void
}

/** Dãy số trang rút gọn quanh trang hiện tại: 1 … 4 [5] 6 … 20 */
function pageWindow(current: number, total: number): (number | 'gap')[] {
  if (total <= 7) return Array.from({ length: total }, (_, i) => i)

  const out: (number | 'gap')[] = [0]
  const from = Math.max(1, current - 1)
  const to = Math.min(total - 2, current + 1)

  if (from > 1) out.push('gap')
  for (let i = from; i <= to; i++) out.push(i)
  if (to < total - 2) out.push('gap')

  out.push(total - 1)
  return out
}

export default function Pagination({ page, totalPages, totalElements, onChange }: Props) {
  if (totalPages <= 1) {
    return (
      <p className="text-2xs text-slate-500 dark:text-slate-400 text-center py-3">
        {formatNumber(totalElements)} kết quả
      </p>
    )
  }

  const items = pageWindow(page, totalPages)

  return (
    <nav className="flex items-center justify-between gap-3 flex-wrap py-3" aria-label="Phân trang">
      <p className="text-2xs text-slate-500 dark:text-slate-400">
        Trang {page + 1}/{totalPages} · {formatNumber(totalElements)} kết quả
      </p>

      <div className="flex items-center gap-1">
        <button
          className="btn btn-secondary btn-sm"
          onClick={() => onChange(page - 1)}
          disabled={page === 0}
          aria-label="Trang trước"
        >
          <ChevronLeft size={14} />
        </button>

        {items.map((it, i) =>
          it === 'gap' ? (
            <span key={`gap-${i}`} className="px-1.5 text-slate-400 text-2xs">
              …
            </span>
          ) : (
            <button
              key={it}
              onClick={() => onChange(it)}
              aria-current={it === page ? 'page' : undefined}
              className={`min-w-8 px-2 py-1.5 rounded-md text-2xs font-bold transition-colors ${
                it === page
                  ? 'bg-primary-600 text-white dark:bg-primary-500'
                  : 'text-slate-600 hover:bg-slate-100 dark:text-slate-300 dark:hover:bg-slate-700/60'
              }`}
            >
              {it + 1}
            </button>
          ),
        )}

        <button
          className="btn btn-secondary btn-sm"
          onClick={() => onChange(page + 1)}
          disabled={page >= totalPages - 1}
          aria-label="Trang sau"
        >
          <ChevronRight size={14} />
        </button>
      </div>
    </nav>
  )
}
