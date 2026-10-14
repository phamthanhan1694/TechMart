'use client'

import { Loader2, type LucideIcon } from 'lucide-react'
import type { ReactNode } from 'react'

/** Vòng xoay chờ, canh giữa trong khối cha. */
export function Loading({ label = 'Đang tải…' }: { label?: string }) {
  return (
    <div className="flex items-center justify-center gap-2 py-12 text-sm text-slate-500 dark:text-slate-400">
      <Loader2 className="animate-spin" size={18} />
      <span>{label}</span>
    </div>
  )
}

/** Chờ chiếm trọn màn hình — dùng cho guard trước khi biết có quyền vào hay không. */
export function FullPageLoading({ label = 'Đang tải…' }: { label?: string }) {
  return (
    <div className="min-h-screen flex items-center justify-center bg-[#e8edf4] dark:bg-surface">
      <div className="flex items-center gap-2 text-slate-500 dark:text-slate-400 text-sm">
        <Loader2 className="animate-spin" size={18} />
        <span>{label}</span>
      </div>
    </div>
  )
}

export function EmptyState({
  icon: Icon,
  title,
  desc,
  action,
}: {
  icon: LucideIcon
  title: string
  desc?: string
  action?: ReactNode
}) {
  return (
    <div className="flex flex-col items-center justify-center text-center py-14 px-4">
      <div className="w-12 h-12 rounded-2xl bg-slate-100 dark:bg-slate-700/50 flex items-center justify-center text-slate-400 dark:text-slate-500 mb-3">
        <Icon size={22} />
      </div>
      <p className="font-semibold text-slate-700 dark:text-slate-200">{title}</p>
      {desc && (
        <p className="text-sm text-slate-500 dark:text-slate-400 mt-1 max-w-sm leading-relaxed">
          {desc}
        </p>
      )}
      {action && <div className="mt-4">{action}</div>}
    </div>
  )
}

/** Khung xám nhấp nháy thay chỗ nội dung đang tải. */
export function Skeleton({ className = '' }: { className?: string }) {
  return (
    <div
      className={`animate-pulse-soft rounded-lg bg-slate-200/80 dark:bg-slate-700/50 ${className}`}
    />
  )
}

export function ErrorBox({ message, code }: { message: string; code?: string }) {
  return (
    <div className="form-error">
      <div className="min-w-0">
        <p>{message}</p>
        {code && <p className="text-2xs font-mono opacity-70 mt-0.5">{code}</p>}
      </div>
    </div>
  )
}
