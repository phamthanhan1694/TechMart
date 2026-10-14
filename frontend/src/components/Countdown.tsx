'use client'

import { useEffect, useRef, useState } from 'react'
import { formatCountdown } from '@/lib/format'

interface Props {
  /** Số giây còn lại do backend trả về. Dùng khi backend đã tính sẵn. */
  seconds?: number
  /** Mốc hết hạn dạng ISO. Dùng khi backend chỉ trả thời điểm, không trả số giây. */
  until?: string
  /** Gọi một lần khi đếm về 0 — ví dụ để nhả phiên giữ hàng. */
  onExpire?: () => void
  className?: string
}

/**
 * Đồng hồ đếm ngược cho phiên giữ hàng và giờ mở bán.
 *
 * Mốc hết hạn tính **một lần trong effect** rồi so với đồng hồ máy mỗi giây,
 * thay vì trừ dần một biến đếm. Lý do: tab chạy nền bị trình duyệt giảm nhịp
 * `setInterval`, trừ dần sẽ chạy chậm lại và hiện số giây còn nhiều hơn thực tế
 * — khách tưởng còn thời gian trong khi backend đã nhả hàng.
 *
 * Đồng hồ chỉ được đọc trong effect, không đọc lúc render: render phải cho ra
 * cùng kết quả với cùng props, nếu không React không thể ghi nhớ lại an toàn.
 * Vì vậy component nhận `until` (mốc ISO) thay vì bắt nơi gọi tự trừ `Date.now()`.
 */
export default function Countdown({ seconds, until, onExpire, className = '' }: Props) {
  const deadline = useRef(0)
  const fired = useRef(false)
  const [left, setLeft] = useState<number | null>(null)

  // Nơi gọi thường truyền arrow inline, đổi định danh mỗi lần render. Để nó trong
  // deps của effect thì interval bị dựng lại liên tục, nên giữ callback mới nhất
  // trong ref và để deps chỉ phụ thuộc vào mốc thời gian.
  const onExpireRef = useRef(onExpire)
  useEffect(() => {
    onExpireRef.current = onExpire
  })

  useEffect(() => {
    deadline.current = until
      ? new Date(until).getTime()
      : Date.now() + (seconds ?? 0) * 1000
    fired.current = false

    const tick = () => {
      const remaining = Math.max(0, Math.round((deadline.current - Date.now()) / 1000))
      setLeft(remaining)
      if (remaining === 0 && !fired.current) {
        fired.current = true
        onExpireRef.current?.()
      }
    }

    tick()
    const id = setInterval(tick, 1000)
    return () => clearInterval(id)
  }, [seconds, until])

  // Lần render đầu chưa chạy effect nên chưa biết số giây; giữ chỗ để khỏi nhảy layout.
  if (left === null) {
    return <span className={`tabular-nums font-bold ${className}`}>--:--</span>
  }

  const urgent = left <= 60

  return (
    <span
      className={`tabular-nums font-bold ${urgent ? 'text-red-600 dark:text-red-400' : ''} ${className}`}
    >
      {formatCountdown(left)}
    </span>
  )
}
