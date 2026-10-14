'use client'

import { useState } from 'react'
import { Table2 } from 'lucide-react'
import { formatDate, formatNumber, formatPrice, formatPriceShort } from '@/lib/format'
import type { RevenuePoint } from '@/types'

/**
 * Doanh thu theo thời gian — một chuỗi số liệu duy nhất.
 *
 * Một chuỗi thì không cần bảng chú giải: tiêu đề đã nói đây là gì, và màu chỉ
 * còn việc phân biệt cột với nền. Dùng đúng một sắc lam, bậc chọn riêng cho
 * từng nền sáng/tối để cột luôn đạt tương phản ≥ 3:1 — không phải lật màu tự động.
 *
 * Có sẵn chế độ xem bảng: người đọc bằng trình đọc màn hình, hoặc cần con số
 * chính xác thay vì ước lượng theo chiều cao cột, không nên bị bỏ lại.
 */
export default function RevenueChart({
  points,
  groupBy,
}: {
  points: RevenuePoint[]
  groupBy: 'day' | 'month'
}) {
  const [asTable, setAsTable] = useState(false)
  const [hover, setHover] = useState<number | null>(null)

  if (points.length === 0) {
    return (
      <p className="text-sm text-slate-500 dark:text-slate-400 text-center py-12">
        Chưa có doanh thu trong khoảng thời gian này.
      </p>
    )
  }

  const max = Math.max(...points.map((p) => p.revenue), 1)
  // Bốn vạch lưới đủ để ước lượng mà không làm nền rối.
  const ticks = [1, 0.75, 0.5, 0.25, 0].map((r) => ({ ratio: r, value: max * r }))

  const label = (p: RevenuePoint) =>
    groupBy === 'month'
      ? new Date(p.period).toLocaleDateString('vi-VN', { month: '2-digit', year: '2-digit' })
      : new Date(p.period).toLocaleDateString('vi-VN', { day: '2-digit', month: '2-digit' })

  // Nhãn trục ngang chỉ hiện thưa ra để không chồng chữ lên nhau.
  const labelEvery = Math.max(1, Math.ceil(points.length / 8))

  if (asTable) {
    return (
      <div className="flex flex-col gap-3">
        <button onClick={() => setAsTable(false)} className="btn btn-secondary btn-sm w-fit">
          <Table2 size={13} /> Xem dạng biểu đồ
        </button>
        <div className="overflow-x-auto max-h-80">
          <table className="data-table">
            <thead>
              <tr>
                <th>Kỳ</th>
                <th>Doanh thu</th>
                <th>Số đơn</th>
                <th>Giá trị TB/đơn</th>
              </tr>
            </thead>
            <tbody>
              {points.map((p) => (
                <tr key={p.period}>
                  <td>{formatDate(p.period)}</td>
                  <td className="tabular-nums font-semibold">{formatPrice(p.revenue)}</td>
                  <td className="tabular-nums">{formatNumber(p.orderCount)}</td>
                  <td className="tabular-nums">{formatPrice(p.averageOrderValue)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    )
  }

  return (
    <div className="flex flex-col gap-3 [--viz-series:#2a78d6] dark:[--viz-series:#3987e5]">
      <button onClick={() => setAsTable(true)} className="btn btn-secondary btn-sm w-fit">
        <Table2 size={13} /> Xem dạng bảng
      </button>

      <div className="flex gap-2">
        {/* Trục dọc */}
        <div className="flex flex-col justify-between h-56 shrink-0 text-right">
          {ticks.map((t) => (
            <span
              key={t.ratio}
              className="text-2xs text-slate-400 dark:text-slate-500 tabular-nums leading-none"
            >
              {formatPriceShort(t.value)}
            </span>
          ))}
        </div>

        {/* Vùng vẽ */}
        <div className="flex-1 min-w-0">
          <div className="relative h-56">
            {/* Lưới — mờ hẳn so với cột, chỉ để ước lượng */}
            {ticks.map((t) => (
              <div
                key={t.ratio}
                className="absolute left-0 right-0 border-t border-slate-100 dark:border-slate-700/60"
                style={{ top: `${(1 - t.ratio) * 100}%` }}
              />
            ))}

            <div className="absolute inset-0 flex items-end gap-0.5">
              {points.map((p, i) => {
                const heightPercent = (p.revenue / max) * 100
                const active = hover === i
                return (
                  <div
                    key={p.period}
                    className="relative flex-1 h-full flex items-end min-w-0"
                    onMouseEnter={() => setHover(i)}
                    onMouseLeave={() => setHover(null)}
                  >
                    <div
                      className="w-full rounded-t transition-opacity"
                      style={{
                        height: `${Math.max(heightPercent, p.revenue > 0 ? 1.5 : 0)}%`,
                        background: 'var(--viz-series)',
                        opacity: hover === null || active ? 1 : 0.45,
                      }}
                    />

                    {active && (
                      <div className="absolute bottom-full left-1/2 -translate-x-1/2 mb-1.5 z-10 whitespace-nowrap px-2.5 py-1.5 rounded-lg text-2xs shadow-lg bg-white border border-slate-200 dark:bg-surface-card dark:border-surface-border pointer-events-none">
                        <p className="font-bold text-slate-800 dark:text-slate-100">
                          {formatDate(p.period)}
                        </p>
                        <p className="text-slate-600 dark:text-slate-300 tabular-nums">
                          {formatPrice(p.revenue)}
                        </p>
                        <p className="text-slate-500 dark:text-slate-400 tabular-nums">
                          {formatNumber(p.orderCount)} đơn
                        </p>
                      </div>
                    )}
                  </div>
                )
              })}
            </div>
          </div>

          {/* Trục ngang */}
          <div className="flex gap-0.5 mt-1.5 border-t border-slate-200 dark:border-slate-700 pt-1.5">
            {points.map((p, i) => (
              <span
                key={p.period}
                className="flex-1 min-w-0 text-center text-2xs text-slate-400 dark:text-slate-500 truncate"
              >
                {i % labelEvery === 0 ? label(p) : ''}
              </span>
            ))}
          </div>
        </div>
      </div>
    </div>
  )
}
