'use client'

import { useMemo, useState } from 'react'
import { BarChart3, Table2 } from 'lucide-react'
import type { HeatCell } from '@/types'

const DAYS = ['T2', 'T3', 'T4', 'T5', 'T6', 'T7', 'CN']
const DAY_NAMES = ['Thứ Hai', 'Thứ Ba', 'Thứ Tư', 'Thứ Năm', 'Thứ Sáu', 'Thứ Bảy', 'Chủ Nhật']
const STEPS = ['var(--viz-seq-1)', 'var(--viz-seq-2)', 'var(--viz-seq-3)', 'var(--viz-seq-4)', 'var(--viz-seq-5)']

interface Props {
  cells: HeatCell[]
  formatValue: (n: number) => string
}

/**
 * Bản đồ nhiệt thứ × giờ: khi nào khách đặt hàng nhiều nhất.
 *
 * Thang tuần tự MỘT sắc lam, chia 5 bậc theo khoảng đều của giá trị lớn nhất. Không dùng
 * thang cầu vồng: mắt không xếp được thứ tự đỏ-vàng-lục thành "nhiều-ít". Ô bằng 0 dùng
 * màu nền riêng, tách khỏi bậc thấp nhất - "không có đơn nào" khác với "có ít đơn".
 *
 * Dùng cho: xếp ca trực chăm sóc khách, chọn giờ mở flash sale, giờ chạy quảng cáo.
 */
export default function HeatmapGrid({ cells, formatValue }: Props) {
  const [asTable, setAsTable] = useState(false)
  const [hover, setHover] = useState<HeatCell | null>(null)

  const { grid, max, peak } = useMemo(() => {
    const g: HeatCell[][] = Array.from({ length: 7 }, (_, d) =>
      Array.from({ length: 24 }, (_, h) => ({ dayOfWeek: d + 1, hour: h, orders: 0, revenue: 0 })),
    )
    for (const c of cells) g[c.dayOfWeek - 1][c.hour] = c
    const m = Math.max(0, ...cells.map((c) => c.orders))
    const p = cells.reduce<HeatCell | null>((best, c) => (!best || c.orders > best.orders ? c : best), null)
    return { grid: g, max: m, peak: p }
  }, [cells])

  const step = (orders: number) =>
    orders <= 0 || max === 0 ? 'var(--viz-empty)' : STEPS[Math.min(4, Math.floor(((orders - 1) / max) * 5))]

  const describe = (c: HeatCell) =>
    `${DAY_NAMES[c.dayOfWeek - 1]}, ${c.hour}:00–${c.hour + 1}:00 · ${c.orders} đơn · ${formatValue(c.revenue)}`

  return (
    <div className="viz flex flex-col gap-3">
      <div className="flex items-center justify-between gap-3 flex-wrap">
        <p className="text-2xs text-slate-500 dark:text-slate-400 min-h-4">
          {hover ? (
            <span className="font-semibold text-slate-700 dark:text-slate-200">{describe(hover)}</span>
          ) : peak ? (
            <>
              Cao điểm: <span className="font-semibold text-slate-700 dark:text-slate-200">{describe(peak)}</span>
            </>
          ) : (
            'Chưa có đơn nào trong khoảng này.'
          )}
        </p>
        <button onClick={() => setAsTable((v) => !v)} className="btn btn-secondary btn-sm">
          {asTable ? <BarChart3 size={13} /> : <Table2 size={13} />}
          {asTable ? 'Xem biểu đồ' : 'Xem bảng'}
        </button>
      </div>

      {asTable ? (
        <div className="overflow-x-auto">
          <table className="data-table text-2xs">
            <thead>
              <tr>
                <th>Thứ</th>
                {Array.from({ length: 24 }, (_, h) => (
                  <th key={h} className="text-right px-1">{h}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {grid.map((row, d) => (
                <tr key={DAYS[d]}>
                  <td className="font-semibold">{DAYS[d]}</td>
                  {row.map((c) => (
                    <td key={c.hour} className="text-right tabular-nums px-1">{c.orders || ''}</td>
                  ))}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      ) : (
        <div className="overflow-x-auto">
          <div className="min-w-[560px]">
            <div className="grid gap-[2px]" style={{ gridTemplateColumns: '2rem repeat(24, minmax(0, 1fr))' }}>
              {grid.map((row, d) => (
                <div key={DAYS[d]} className="contents">
                  <span className="text-2xs text-slate-500 dark:text-slate-400 self-center">{DAYS[d]}</span>
                  {row.map((c) => (
                    <button
                      key={c.hour}
                      type="button"
                      aria-label={describe(c)}
                      onMouseEnter={() => setHover(c)}
                      onMouseLeave={() => setHover(null)}
                      onFocus={() => setHover(c)}
                      onBlur={() => setHover(null)}
                      className="aspect-square rounded-[3px] outline-none focus-visible:ring-2 focus-visible:ring-primary-500 hover:ring-2 hover:ring-slate-400 dark:hover:ring-slate-300"
                      style={{ background: step(c.orders) }}
                    />
                  ))}
                </div>
              ))}
              <span />
              {Array.from({ length: 24 }, (_, h) => (
                <span key={h} className="text-[10px] text-center text-slate-400 dark:text-slate-500">
                  {h % 3 === 0 ? h : ''}
                </span>
              ))}
            </div>
          </div>
        </div>
      )}

      {!asTable && max > 0 && (
        <div className="flex items-center gap-2 text-2xs text-slate-500 dark:text-slate-400">
          <span>0</span>
          <span className="w-3 h-3 rounded-[3px]" style={{ background: 'var(--viz-empty)' }} />
          {STEPS.map((s) => (
            <span key={s} className="w-3 h-3 rounded-[3px]" style={{ background: s }} />
          ))}
          <span>{max} đơn/giờ</span>
        </div>
      )}
    </div>
  )
}
