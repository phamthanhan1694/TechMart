'use client'

import { Suspense } from 'react'
import Link from 'next/link'
import { Cpu } from 'lucide-react'
import Header from '@/components/Header'

/**
 * Khung chung của phần cửa hàng: header dính trên, nội dung, chân trang.
 *
 * Header đọc `useSearchParams` nên phải nằm trong <Suspense> — Next yêu cầu vậy
 * để phần còn lại của trang vẫn dựng tĩnh được.
 */
export default function StoreShell({
  children,
  /** Bỏ giới hạn bề ngang khi trang tự lo bố cục (ví dụ trang chủ có băng rôn tràn viền). */
  fullWidth,
}: {
  children: React.ReactNode
  fullWidth?: boolean
}) {
  return (
    <div className="min-h-screen flex flex-col">
      <Suspense fallback={<div className="h-14 border-b border-slate-200 dark:border-surface-border" />}>
        <Header />
      </Suspense>

      <main className={fullWidth ? 'flex-1' : 'flex-1 mx-auto w-full max-w-6xl px-4 py-6'}>
        {children}
      </main>

      <footer className="border-t border-slate-200 dark:border-surface-border mt-8">
        <div className="mx-auto max-w-6xl px-4 py-6 flex flex-col sm:flex-row items-start sm:items-center justify-between gap-3">
          <div className="flex items-center gap-2">
            <span className="w-7 h-7 rounded-lg bg-primary-600 dark:bg-primary-500 text-white flex items-center justify-center">
              <Cpu size={15} />
            </span>
            <div>
              <p className="font-bold text-sm text-slate-700 dark:text-slate-200">TechMart</p>
              <p className="text-2xs text-slate-500 dark:text-slate-400">
                Điện thoại &amp; laptop chính hãng
              </p>
            </div>
          </div>

          <nav className="flex flex-wrap items-center gap-x-4 gap-y-1 text-2xs text-slate-500 dark:text-slate-400">
            <Link href="/products" className="hover:text-primary-600 dark:hover:text-primary-400">
              Sản phẩm
            </Link>
            <Link href="/flash-sales" className="hover:text-primary-600 dark:hover:text-primary-400">
              Flash sale
            </Link>
            <Link href="/warranty" className="hover:text-primary-600 dark:hover:text-primary-400">
              Tra cứu bảo hành
            </Link>
            <Link href="/compare" className="hover:text-primary-600 dark:hover:text-primary-400">
              So sánh
            </Link>
          </nav>
        </div>
      </footer>
    </div>
  )
}
