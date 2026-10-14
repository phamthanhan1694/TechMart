'use client'

import {
  createContext,
  useCallback,
  useContext,
  useRef,
  useState,
  type ReactNode,
} from 'react'
import { AlertCircle, CheckCircle2, Info, X } from 'lucide-react'
import { ApiError } from '@/lib/api/client'

type ToastKind = 'success' | 'error' | 'info'

interface Toast {
  id: number
  kind: ToastKind
  message: string
  /** Ma loi cua backend - hien nho ben duoi de doi chieu voi docs/04-error-catalog.md. */
  code?: string
}

interface ToastContextType {
  success: (message: string) => void
  info: (message: string) => void
  error: (message: string, code?: string) => void
  /** Bat loi tu API: uu tien message cua backend, kem ma loi. */
  showError: (err: unknown, fallback?: string) => void
}

const ToastContext = createContext<ToastContextType | null>(null)

const ICONS: Record<ToastKind, typeof Info> = {
  success: CheckCircle2,
  error: AlertCircle,
  info: Info,
}

const STYLES: Record<ToastKind, string> = {
  success:
    'bg-emerald-50 border-emerald-200 text-emerald-800 dark:bg-emerald-900/30 dark:border-emerald-800 dark:text-emerald-200',
  error:
    'bg-red-50 border-red-200 text-red-700 dark:bg-red-900/30 dark:border-red-800 dark:text-red-200',
  info: 'bg-white border-slate-200 text-slate-700 dark:bg-surface-card dark:border-surface-border dark:text-slate-200',
}

export function ToastProvider({ children }: { children: ReactNode }) {
  const [toasts, setToasts] = useState<Toast[]>([])
  const nextId = useRef(1)

  const dismiss = useCallback((id: number) => {
    setToasts((prev) => prev.filter((t) => t.id !== id))
  }, [])

  const push = useCallback(
    (kind: ToastKind, message: string, code?: string) => {
      const id = nextId.current++
      setToasts((prev) => [...prev, { id, kind, message, code }])
      // Loi de lau hon mot chut - thuong co ma loi can doc.
      setTimeout(() => dismiss(id), kind === 'error' ? 6000 : 3500)
    },
    [dismiss],
  )

  const success = useCallback((m: string) => push('success', m), [push])
  const info = useCallback((m: string) => push('info', m), [push])
  const error = useCallback((m: string, code?: string) => push('error', m, code), [push])

  const showError = useCallback(
    (err: unknown, fallback = 'Đã có lỗi xảy ra, vui lòng thử lại') => {
      if (err instanceof ApiError) {
        push('error', err.message || fallback, err.code)
        return
      }
      push('error', err instanceof Error ? err.message : fallback)
    },
    [push],
  )

  return (
    <ToastContext.Provider value={{ success, info, error, showError }}>
      {children}

      <div
        className="fixed bottom-4 right-4 z-[100] flex flex-col gap-2 w-[min(22rem,calc(100vw-2rem))]"
        role="status"
        aria-live="polite"
      >
        {toasts.map((t) => {
          const Icon = ICONS[t.kind]
          return (
            <div
              key={t.id}
              className={`flex items-start gap-2.5 px-3.5 py-3 rounded-xl border shadow-lg text-sm animate-slide-up ${STYLES[t.kind]}`}
            >
              <Icon size={16} className="shrink-0 mt-0.5" />
              <div className="flex-1 min-w-0">
                <p className="leading-snug break-words">{t.message}</p>
                {t.code && (
                  <p className="text-2xs opacity-70 font-mono mt-0.5">{t.code}</p>
                )}
              </div>
              <button
                onClick={() => dismiss(t.id)}
                className="shrink-0 opacity-60 hover:opacity-100 transition-opacity"
                aria-label="Đóng"
              >
                <X size={14} />
              </button>
            </div>
          )
        })}
      </div>
    </ToastContext.Provider>
  )
}

export function useToast(): ToastContextType {
  const ctx = useContext(ToastContext)
  if (!ctx) throw new Error('useToast must be used inside <ToastProvider>')
  return ctx
}
