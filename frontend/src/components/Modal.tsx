'use client'

import { useEffect } from 'react'
import { X } from 'lucide-react'
import type { ReactNode } from 'react'

interface Props {
  open: boolean
  title: string
  onClose: () => void
  children: ReactNode
  footer?: ReactNode
  /** Hộp thoại rộng hơn cho biểu mẫu nhiều trường. */
  wide?: boolean
}

export default function Modal({ open, title, onClose, children, footer, wide }: Props) {
  // Esc để đóng, và khoá cuộn nền để hộp thoại không trôi theo trang phía sau.
  useEffect(() => {
    if (!open) return
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose()
    }
    document.addEventListener('keydown', onKey)
    const prev = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    return () => {
      document.removeEventListener('keydown', onKey)
      document.body.style.overflow = prev
    }
  }, [open, onClose])

  if (!open) return null

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-900/50 backdrop-blur-sm animate-fade-in"
      onClick={onClose}
      role="dialog"
      aria-modal="true"
      aria-label={title}
    >
      <div
        className={`card w-full ${wide ? 'max-w-2xl' : 'max-w-md'} max-h-[85vh] flex flex-col animate-fade-in-down`}
        onClick={(e) => e.stopPropagation()}
      >
        <header className="flex items-center justify-between gap-3 px-5 py-3.5 border-b border-slate-200 dark:border-slate-700">
          <h2 className="text-base font-bold text-slate-800 dark:text-slate-100">{title}</h2>
          <button onClick={onClose} className="btn-ghost p-1 rounded-md" aria-label="Đóng">
            <X size={16} />
          </button>
        </header>

        <div className="px-5 py-4 overflow-y-auto flex-1">{children}</div>

        {footer && (
          <footer className="flex items-center justify-end gap-2 px-5 py-3.5 border-t border-slate-200 dark:border-slate-700">
            {footer}
          </footer>
        )}
      </div>
    </div>
  )
}
