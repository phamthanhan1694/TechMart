'use client'

import type { LucideIcon } from 'lucide-react'

export interface ShareSegment {
  key: string
  label: string
  value: number
  display: string
  color: string
  /** Bắt buộc khi màu mang nghĩa trạng thái (tốt/xấu): màu không bao giờ tự nói một mình. */
  icon?: LucideIcon
}

/**
 * Một thanh 100% chia phần - cơ cấu "phần trên tổng" với 2-3 phần.
 *
 * Chọn thanh ngang thay vì bánh donut: so hai phần gần bằng nhau (48% với 52%) bằng góc
 * cung là việc mắt người làm kém nhất; bằng chiều dài thì dễ. Các phần tách nhau bằng khe
 * 2px màu nền, không kẻ viền. Chú giải luôn hiện kèm số.
 */
export default function ShareBar({ segments, ariaLabel }: { segments: ShareSegment[]; ariaLabel: string }) {
  const total = segments.reduce((s, x) => s + x.value, 0)
  if (total <= 0) {
    return <p className="text-sm text-slate-500 dark:text-slate-400 text-center py-6">Chưa có dữ liệu.</p>
  }
  return (
    <div className="viz flex flex-col gap-3">
      <div className="flex h-3 w-full gap-[2px]" role="img" aria-label={ariaLabel}>
        {segments
          .filter((s) => s.value > 0)
          .map((s, i, arr) => (
            <div
              key={s.key}
              title={`${s.label}: ${s.display}`}
              className={`h-full ${i === 0 ? 'rounded-l-full' : ''} ${i === arr.length - 1 ? 'rounded-r-full' : ''}`}
              style={{ width: `${(s.value / total) * 100}%`, background: s.color }}
            />
          ))}
      </div>
      <ul className="flex flex-wrap gap-x-5 gap-y-1.5">
        {segments.map((s) => {
          const Icon = s.icon
          return (
            <li key={s.key} className="flex items-center gap-1.5 text-2xs">
              <span className="w-2.5 h-2.5 rounded-sm shrink-0" style={{ background: s.color }} />
              {Icon && <Icon size={12} className="text-slate-500 dark:text-slate-400" aria-hidden />}
              <span className="text-slate-600 dark:text-slate-300">{s.label}</span>
              <span className="font-bold text-slate-800 dark:text-slate-100 tabular-nums">{s.display}</span>
            </li>
          )
        })}
      </ul>
    </div>
  )
}
