'use client'

import { Star } from 'lucide-react'

interface Props {
  value: number
  size?: number
  /** Bật để người dùng bấm chọn sao (form viết đánh giá). */
  onChange?: (rating: number) => void
  className?: string
}

export default function StarRating({ value, size = 14, onChange, className = '' }: Props) {
  const interactive = typeof onChange === 'function'

  return (
    <div className={`inline-flex items-center gap-0.5 ${className}`} role={interactive ? 'radiogroup' : 'img'}
         aria-label={interactive ? 'Chọn số sao' : `${value} trên 5 sao`}>
      {[1, 2, 3, 4, 5].map((n) => {
        const filled = n <= Math.round(value)
        const star = (
          <Star
            size={size}
            className={
              filled
                ? 'fill-amber-400 text-amber-400'
                : 'text-slate-300 dark:text-slate-600'
            }
          />
        )
        return interactive ? (
          <button
            key={n}
            type="button"
            onClick={() => onChange(n)}
            className="p-0.5 rounded transition-transform hover:scale-110 cursor-pointer"
            role="radio"
            aria-checked={n === Math.round(value)}
            aria-label={`${n} sao`}
          >
            {star}
          </button>
        ) : (
          <span key={n}>{star}</span>
        )
      })}
    </div>
  )
}
