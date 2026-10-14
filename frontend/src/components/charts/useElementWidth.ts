'use client'

import { useEffect, useRef, useState } from 'react'

/**
 * Đo bề ngang thật của khung chứa để vẽ SVG đúng pixel.
 *
 * Không dùng viewBox co giãn (preserveAspectRatio="none"): kéo giãn SVG thì nét 2px thành
 * 5px ở màn rộng, chữ trục bị bóp méo. Đo rồi vẽ theo số đo thì nét và chữ luôn đúng cỡ.
 */
export function useElementWidth<T extends HTMLElement>(fallback = 640) {
  const ref = useRef<T>(null)
  const [width, setWidth] = useState(fallback)

  useEffect(() => {
    const el = ref.current
    if (!el) return
    const update = () => setWidth(Math.max(240, Math.floor(el.getBoundingClientRect().width)))
    update()
    const observer = new ResizeObserver(update)
    observer.observe(el)
    return () => observer.disconnect()
  }, [])

  return { ref, width }
}

/** Mốc trục "tròn": 0, 5 tr, 10 tr… thay vì 0, 4,37 tr, 8,74 tr. */
export function niceTicks(max: number, count = 4): number[] {
  if (max <= 0) return [0]
  const rough = max / count
  const magnitude = 10 ** Math.floor(Math.log10(rough))
  const step = [1, 2, 2.5, 5, 10].map((m) => m * magnitude).find((s) => s >= rough) ?? rough
  const ticks: number[] = []
  for (let v = 0; v <= max + step * 0.001; v += step) ticks.push(v)
  if (ticks[ticks.length - 1] < max) ticks.push(ticks[ticks.length - 1] + step)
  return ticks
}
