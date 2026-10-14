'use client'

import { useCallback, useEffect, useRef, useState } from 'react'
import { useRouter } from 'next/navigation'
import { Bell, CheckCheck, Loader2 } from 'lucide-react'
import * as engagementApi from '@/lib/api/engagement'
import { formatRelative } from '@/lib/format'
import type { NotificationItem } from '@/types'

/** 30 giây: đủ nhanh cho "đơn đã giao", đủ thưa để 10.000 khách mở tab cùng lúc chỉ là ~330 req/s rất rẻ. */
const POLL_MS = 30_000

/**
 * Chuông thông báo - polling, không WebSocket (ADR-0013).
 *
 * Chỉ hỏi khi tab đang hiển thị: tab ẩn thì dừng, quay lại thì hỏi ngay một lần. Hàng chục
 * tab bỏ quên ở nền không được biến thành tải thật lên backend.
 */
export default function NotificationBell() {
  const router = useRouter()
  const [count, setCount] = useState(0)
  const [open, setOpen] = useState(false)
  const [items, setItems] = useState<NotificationItem[] | null>(null)
  const [loading, setLoading] = useState(false)
  const boxRef = useRef<HTMLDivElement>(null)

  const refreshCount = useCallback(async () => {
    try {
      setCount((await engagementApi.unreadCount()).count)
    } catch {
      // Chuông lỗi không được làm hỏng trang đang xem
    }
  }, [])

  useEffect(() => {
    void refreshCount()
    let timer: ReturnType<typeof setInterval> | null = null
    const start = () => {
      if (timer == null) timer = setInterval(() => void refreshCount(), POLL_MS)
    }
    const stop = () => {
      if (timer != null) clearInterval(timer)
      timer = null
    }
    const onVisibility = () => {
      if (document.visibilityState === 'visible') {
        void refreshCount()
        start()
      } else {
        stop()
      }
    }
    if (document.visibilityState === 'visible') start()
    document.addEventListener('visibilitychange', onVisibility)
    return () => {
      stop()
      document.removeEventListener('visibilitychange', onVisibility)
    }
  }, [refreshCount])

  useEffect(() => {
    if (!open) return
    const onClick = (e: MouseEvent) => {
      if (boxRef.current && !boxRef.current.contains(e.target as Node)) setOpen(false)
    }
    document.addEventListener('mousedown', onClick)
    return () => document.removeEventListener('mousedown', onClick)
  }, [open])

  const toggle = async () => {
    const next = !open
    setOpen(next)
    if (!next) return
    setLoading(true)
    try {
      setItems((await engagementApi.notifications(0, 8)).content)
    } catch {
      setItems([])
    } finally {
      setLoading(false)
    }
  }

  const openItem = async (n: NotificationItem) => {
    if (!n.read) {
      await engagementApi.markRead(n.id).catch(() => undefined)
      setCount((c) => Math.max(0, c - 1))
    }
    setOpen(false)
    if (n.link) router.push(n.link)
  }

  const readAll = async () => {
    await engagementApi.markAllRead().catch(() => undefined)
    setCount(0)
    setItems((list) => list?.map((n) => ({ ...n, read: true })) ?? null)
  }

  return (
    <div className="relative" ref={boxRef}>
      <button
        onClick={() => void toggle()}
        className="relative btn-ghost p-2 rounded-lg"
        aria-label={count > 0 ? `Thông báo, ${count} chưa đọc` : 'Thông báo'}
        aria-expanded={open}
      >
        <Bell size={17} />
        {count > 0 && (
          <span className="absolute -top-0.5 -right-0.5 min-w-4 h-4 px-1 rounded-full bg-primary-600 text-white text-[10px] font-bold flex items-center justify-center">
            {count > 99 ? '99+' : count}
          </span>
        )}
      </button>

      {open && (
        <div className="absolute right-0 top-full mt-1.5 w-80 max-w-[calc(100vw-2rem)] card shadow-lg animate-fade-in-down">
          <div className="flex items-center justify-between px-3.5 py-2.5 border-b border-slate-100 dark:border-slate-700">
            <p className="text-sm font-bold text-slate-800 dark:text-slate-100">Thông báo</p>
            {count > 0 && (
              <button onClick={() => void readAll()} className="text-2xs font-semibold text-primary-600 dark:text-primary-400 flex items-center gap-1 hover:underline">
                <CheckCheck size={13} /> Đọc hết
              </button>
            )}
          </div>
          <div className="max-h-96 overflow-y-auto">
            {loading ? (
              <div className="flex justify-center py-6 text-slate-400"><Loader2 size={16} className="animate-spin" /></div>
            ) : !items || items.length === 0 ? (
              <p className="text-sm text-slate-500 dark:text-slate-400 text-center py-8">Chưa có thông báo nào.</p>
            ) : (
              <ul>
                {items.map((n) => (
                  <li key={n.id}>
                    <button
                      onClick={() => void openItem(n)}
                      className="w-full text-left px-3.5 py-2.5 flex gap-2.5 hover:bg-slate-50 dark:hover:bg-surface-hover transition-colors"
                    >
                      <span
                        className={`mt-1.5 w-2 h-2 rounded-full shrink-0 ${n.read ? 'bg-transparent' : 'bg-primary-600 dark:bg-primary-400'}`}
                        aria-label={n.read ? undefined : 'Chưa đọc'}
                      />
                      <span className="min-w-0">
                        <span className={`block text-sm ${n.read ? 'text-slate-600 dark:text-slate-300' : 'font-bold text-slate-800 dark:text-slate-100'}`}>
                          {n.title}
                        </span>
                        {n.body && <span className="block text-2xs text-slate-500 dark:text-slate-400 mt-0.5">{n.body}</span>}
                        <span className="block text-[10px] text-slate-400 dark:text-slate-500 mt-0.5">{formatRelative(n.createdAt)}</span>
                      </span>
                    </button>
                  </li>
                ))}
              </ul>
            )}
          </div>
        </div>
      )}
    </div>
  )
}
