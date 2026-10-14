'use client'

import { ArrowDownRight, ArrowUpRight, Minus } from 'lucide-react'
import { formatPercent } from '@/lib/format'
import type { Kpi } from '@/types'

interface Props {
  label: string
  kpi: Kpi
  format: (n: number) => string
  /**
   * Tăng là tốt hay xấu. Doanh thu tăng = tốt; tỉ lệ huỷ tăng = xấu.
   * Màu của mũi tên đi theo "tốt/xấu", không theo "lên/xuống".
   */
  upIsGood?: boolean
  hint?: string
}

/**
 * Ô chỉ số: giá trị kỳ này + chênh lệch so với kỳ trước cùng độ dài.
 *
 * Chênh lệch luôn đi kèm mũi tên VÀ chữ - không bao giờ để màu xanh/đỏ tự nói, vì người
 * mù màu đỏ-lục không phân biệt được. Kỳ trước bằng 0 thì backend trả null và ô này ghi
 * "chưa có kỳ trước" thay vì bịa ra "+100%".
 */
export default function KpiTile({ label, kpi, format, upIsGood = true, hint }: Props) {
  const change = kpi.changePercent
  const direction = change == null || change === 0 ? 'flat' : change > 0 ? 'up' : 'down'
  const good = direction === 'flat' ? null : (direction === 'up') === upIsGood

  const Icon = direction === 'up' ? ArrowUpRight : direction === 'down' ? ArrowDownRight : Minus
  const tone =
    good == null
      ? 'text-slate-500 dark:text-slate-400'
      : good
        ? 'text-emerald-700 dark:text-emerald-400'
        : 'text-red-600 dark:text-red-400'

  return (
    <div className="card-padded flex flex-col gap-1 min-w-0">
      <p className="section-label truncate">{label}</p>
      {/* Số lớn đứng một mình: chữ số tỉ lệ, không tabular-nums */}
      <p className="text-xl font-extrabold text-slate-900 dark:text-slate-50 leading-tight truncate">
        {format(kpi.current)}
      </p>
      <p className={`flex items-center gap-1 text-2xs font-semibold ${tone}`}>
        {change == null ? (
          <span className="text-slate-500 dark:text-slate-400 font-normal">Chưa có số liệu kỳ trước</span>
        ) : (
          <>
            <Icon size={13} aria-hidden />
            <span>
              {change > 0 ? '+' : ''}
              {formatPercent(change, 1)}
            </span>
            <span className="font-normal text-slate-500 dark:text-slate-400">
              so với kỳ trước ({format(kpi.previous)})
            </span>
          </>
        )}
      </p>
      {hint && <p className="text-2xs text-slate-400 dark:text-slate-500">{hint}</p>}
    </div>
  )
}
