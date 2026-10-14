'use client'

import { useState } from 'react'
import { BarChart3, Table2 } from 'lucide-react'
import { niceTicks, useElementWidth } from '@/components/charts/useElementWidth'

export interface TrendPoint {
  /** Nhãn ngắn cho trục ngang, vd "12/09". */
  label: string
  /** Nhãn đầy đủ cho tooltip và bảng, vd "Thứ Sáu, 12/09/2026". */
  fullLabel: string
  current: number
  /** Giá trị cùng vị trí ở kỳ trước; bỏ trống nếu không so sánh. */
  previous?: number | null
  previousLabel?: string
}

interface Props {
  points: TrendPoint[]
  /** Định dạng rút gọn cho trục dọc. */
  formatAxis: (n: number) => string
  /** Định dạng đầy đủ cho tooltip và bảng. */
  formatValue: (n: number) => string
  metricName: string
  height?: number
}

const MARGIN = { top: 12, right: 14, bottom: 26, left: 60 }

/**
 * Xu hướng theo thời gian: kỳ này (lam, có vùng tô nhạt) so với kỳ trước (xám).
 *
 * Kiểu "nhấn một, làm mờ phần còn lại": kỳ này là câu chuyện, kỳ trước chỉ là bối cảnh -
 * nên kỳ trước màu xám chứ không phải một màu thứ hai tranh sự chú ý. Một trục dọc duy nhất:
 * hai kỳ cùng đơn vị nên so thẳng được, không bao giờ vẽ hai trục.
 *
 * Tương tác: rê chuột hay dùng phím ← → đều di chuyển đường dóng; tooltip liệt kê cả hai kỳ.
 * Tooltip không phải đường duy nhất để đọc số - luôn có chế độ xem bảng.
 */
export default function TrendChart({ points, formatAxis, formatValue, metricName, height = 240 }: Props) {
  const { ref, width } = useElementWidth<HTMLDivElement>()
  const [active, setActive] = useState<number | null>(null)
  const [asTable, setAsTable] = useState(false)

  const hasPrevious = points.some((p) => p.previous != null)
  const n = points.length

  if (n === 0) {
    return <p className="text-sm text-slate-500 dark:text-slate-400 text-center py-12">Chưa có dữ liệu.</p>
  }

  const plotW = width - MARGIN.left - MARGIN.right
  const plotH = height - MARGIN.top - MARGIN.bottom
  const max = Math.max(1, ...points.map((p) => Math.max(p.current, p.previous ?? 0)))
  const ticks = niceTicks(max, 4)
  const yMax = ticks[ticks.length - 1] || 1

  const x = (i: number) => MARGIN.left + (n === 1 ? plotW / 2 : (i * plotW) / (n - 1))
  const y = (v: number) => MARGIN.top + plotH - (v / yMax) * plotH

  const line = (get: (p: TrendPoint) => number | null | undefined) =>
    points
      .map((p, i) => {
        const v = get(p)
        return v == null ? null : `${x(i).toFixed(1)},${y(v).toFixed(1)}`
      })
      .filter(Boolean)
      .join(' L ')

  const currentPath = `M ${line((p) => p.current)}`
  const areaPath = `${currentPath} L ${x(n - 1).toFixed(1)},${y(0)} L ${x(0).toFixed(1)},${y(0)} Z`
  const previousPath = hasPrevious ? `M ${line((p) => p.previous)}` : null

  const labelEvery = Math.max(1, Math.ceil(n / Math.max(2, Math.floor(plotW / 70))))

  const pickIndex = (clientX: number, rect: DOMRect) => {
    const rel = clientX - rect.left - MARGIN.left
    const i = n === 1 ? 0 : Math.round((rel / plotW) * (n - 1))
    setActive(Math.min(n - 1, Math.max(0, i)))
  }

  const onKey = (e: React.KeyboardEvent) => {
    if (e.key === 'ArrowRight') setActive((a) => Math.min(n - 1, (a ?? -1) + 1))
    else if (e.key === 'ArrowLeft') setActive((a) => Math.max(0, (a ?? n) - 1))
    else if (e.key === 'Escape') setActive(null)
    else return
    e.preventDefault()
  }

  const toggle = (
    <button onClick={() => setAsTable((v) => !v)} className="btn btn-secondary btn-sm">
      {asTable ? <BarChart3 size={13} /> : <Table2 size={13} />}
      {asTable ? 'Xem biểu đồ' : 'Xem bảng'}
    </button>
  )

  if (asTable) {
    return (
      <div className="flex flex-col gap-3">
        <div>{toggle}</div>
        <div className="overflow-x-auto max-h-80">
          <table className="data-table">
            <thead>
              <tr>
                <th>Kỳ</th>
                <th className="text-right">{metricName}</th>
                {hasPrevious && <th className="text-right">Kỳ trước</th>}
              </tr>
            </thead>
            <tbody>
              {points.map((p) => (
                <tr key={p.fullLabel}>
                  <td className="whitespace-nowrap">{p.fullLabel}</td>
                  <td className="text-right tabular-nums font-semibold">{formatValue(p.current)}</td>
                  {hasPrevious && (
                    <td className="text-right tabular-nums text-slate-500 dark:text-slate-400">
                      {p.previous != null ? formatValue(p.previous) : '—'}
                    </td>
                  )}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    )
  }

  const a = active != null ? points[active] : null
  // Tooltip lật sang trái khi đường dóng đã qua nửa phải, để không tràn khỏi khung.
  const tooltipLeft = active != null ? x(active) : 0
  const flip = tooltipLeft > width / 2

  return (
    <div className="viz flex flex-col gap-3">
      <div className="flex items-center justify-between gap-3 flex-wrap">
        {hasPrevious ? (
          <ul className="flex items-center gap-4 text-2xs text-slate-600 dark:text-slate-300">
            <li className="flex items-center gap-1.5">
              <span className="w-4 h-0.5 rounded-full" style={{ background: 'var(--viz-1)' }} />
              Kỳ này
            </li>
            <li className="flex items-center gap-1.5">
              <span className="w-4 h-0.5 rounded-full" style={{ background: 'var(--viz-prev)' }} />
              Kỳ trước
            </li>
          </ul>
        ) : (
          <span />
        )}
        {toggle}
      </div>

      <div
        ref={ref}
        className="relative outline-none focus-visible:ring-2 focus-visible:ring-primary-500 rounded-lg"
        tabIndex={0}
        role="img"
        aria-label={`${metricName} theo thời gian, ${n} mốc. Dùng phím mũi tên trái phải để đọc từng mốc, hoặc bấm Xem bảng.`}
        onKeyDown={onKey}
        onBlur={() => setActive(null)}
      >
        <svg width={width} height={height} className="block">
          {/* Lưới ngang: nét mảnh, liền, nhạt hơn hẳn dữ liệu */}
          {ticks.map((t) => (
            <g key={t}>
              <line x1={MARGIN.left} x2={width - MARGIN.right} y1={y(t)} y2={y(t)} stroke="var(--viz-grid)" strokeWidth={1} />
              <text
                x={MARGIN.left - 8}
                y={y(t)}
                textAnchor="end"
                dominantBaseline="middle"
                className="fill-slate-400 dark:fill-slate-500 text-[10px] tabular-nums"
              >
                {formatAxis(t)}
              </text>
            </g>
          ))}
          <line
            x1={MARGIN.left}
            x2={width - MARGIN.right}
            y1={y(0)}
            y2={y(0)}
            stroke="var(--viz-axis)"
            strokeWidth={1}
          />

          {points.map((p, i) =>
            i % labelEvery === 0 || i === n - 1 ? (
              <text
                key={p.fullLabel}
                x={x(i)}
                y={height - 8}
                textAnchor={i === 0 ? 'start' : i === n - 1 ? 'end' : 'middle'}
                className="fill-slate-400 dark:fill-slate-500 text-[10px]"
              >
                {p.label}
              </text>
            ) : null,
          )}

          {previousPath && (
            <path d={previousPath} fill="none" stroke="var(--viz-prev)" strokeWidth={2} strokeLinejoin="round" strokeLinecap="round" />
          )}
          <path d={areaPath} fill="var(--viz-1)" fillOpacity={0.1} />
          <path d={currentPath} fill="none" stroke="var(--viz-1)" strokeWidth={2} strokeLinejoin="round" strokeLinecap="round" />

          {/* Chỉ một mốc cuối được đánh dấu sẵn - nhãn chọn lọc, không rải số lên mọi điểm */}
          <circle cx={x(n - 1)} cy={y(points[n - 1].current)} r={4} fill="var(--viz-1)" stroke="var(--viz-surface)" strokeWidth={2} />

          {a && active != null && (
            <g pointerEvents="none">
              <line x1={x(active)} x2={x(active)} y1={MARGIN.top} y2={y(0)} stroke="var(--viz-axis)" strokeWidth={1} />
              {a.previous != null && (
                <circle cx={x(active)} cy={y(a.previous)} r={4} fill="var(--viz-prev)" stroke="var(--viz-surface)" strokeWidth={2} />
              )}
              <circle cx={x(active)} cy={y(a.current)} r={4.5} fill="var(--viz-1)" stroke="var(--viz-surface)" strokeWidth={2} />
            </g>
          )}

          {/* Lớp bắt chuột phủ cả vùng vẽ: người đọc nhắm vào một ngày, không phải một nét 2px */}
          <rect
            x={MARGIN.left}
            y={MARGIN.top}
            width={plotW}
            height={plotH}
            fill="transparent"
            onPointerMove={(e) => pickIndex(e.clientX, e.currentTarget.ownerSVGElement!.getBoundingClientRect())}
            onPointerLeave={() => setActive(null)}
          />
        </svg>

        {a && active != null && (
          <div
            className="absolute z-10 pointer-events-none px-2.5 py-2 rounded-lg shadow-lg text-2xs bg-white border border-slate-200 dark:bg-surface-card dark:border-surface-border whitespace-nowrap"
            style={{
              top: MARGIN.top,
              left: flip ? undefined : tooltipLeft + 10,
              right: flip ? width - tooltipLeft + 10 : undefined,
            }}
          >
            <p className="text-slate-500 dark:text-slate-400 mb-1">{a.fullLabel}</p>
            <p className="flex items-center gap-2">
              <span className="w-3 h-0.5 rounded-full" style={{ background: 'var(--viz-1)' }} />
              <span className="font-bold text-slate-900 dark:text-slate-50 tabular-nums">{formatValue(a.current)}</span>
              <span className="text-slate-500 dark:text-slate-400">kỳ này</span>
            </p>
            {a.previous != null && (
              <p className="flex items-center gap-2 mt-0.5">
                <span className="w-3 h-0.5 rounded-full" style={{ background: 'var(--viz-prev)' }} />
                <span className="font-bold text-slate-700 dark:text-slate-200 tabular-nums">{formatValue(a.previous)}</span>
                <span className="text-slate-500 dark:text-slate-400">{a.previousLabel ?? 'kỳ trước'}</span>
              </p>
            )}
          </div>
        )}
      </div>
    </div>
  )
}
