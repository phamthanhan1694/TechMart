'use client'

import { Minus, Plus } from 'lucide-react'
import { MAX_QUANTITY_PER_ITEM } from '@/types'

interface Props {
  value: number
  onChange: (quantity: number) => void
  /** Hàng còn mua được — chặn ngay ở giao diện thay vì để backend trả 409. */
  max?: number
  disabled?: boolean
}

export default function QuantityStepper({ value, onChange, max, disabled }: Props) {
  // Backend chặn 10/dòng (CartDtos.MAX_QUANTITY_PER_ITEM); tồn kho có thể còn ít hơn.
  const ceiling = Math.max(1, Math.min(MAX_QUANTITY_PER_ITEM, max ?? MAX_QUANTITY_PER_ITEM))

  const set = (n: number) => onChange(Math.max(1, Math.min(ceiling, n)))

  return (
    <div className="inline-flex items-center rounded-lg border border-slate-200 dark:border-surface-border overflow-hidden">
      <button
        type="button"
        onClick={() => set(value - 1)}
        disabled={disabled || value <= 1}
        className="px-2 py-1.5 text-slate-500 hover:bg-slate-100 dark:text-slate-400 dark:hover:bg-slate-700/60 disabled:opacity-40 disabled:cursor-not-allowed transition-colors"
        aria-label="Giảm số lượng"
      >
        <Minus size={13} />
      </button>

      <input
        type="number"
        value={value}
        min={1}
        max={ceiling}
        disabled={disabled}
        onChange={(e) => {
          const n = Number(e.target.value)
          if (!Number.isNaN(n)) set(n)
        }}
        className="w-11 text-center text-sm font-semibold bg-transparent outline-none tabular-nums text-slate-800 dark:text-slate-100 disabled:opacity-50"
        aria-label="Số lượng"
      />

      <button
        type="button"
        onClick={() => set(value + 1)}
        disabled={disabled || value >= ceiling}
        className="px-2 py-1.5 text-slate-500 hover:bg-slate-100 dark:text-slate-400 dark:hover:bg-slate-700/60 disabled:opacity-40 disabled:cursor-not-allowed transition-colors"
        aria-label="Tăng số lượng"
      >
        <Plus size={13} />
      </button>
    </div>
  )
}
