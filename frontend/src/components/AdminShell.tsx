'use client'

import { useState } from 'react'
import Link from 'next/link'
import { usePathname } from 'next/navigation'
import {
  BadgePercent,
  BarChart3,
  Boxes,
  ClipboardList,
  Cpu,
  LayoutDashboard,
  LogOut,
  Menu,
  Package,
  ScanBarcode,
  ShieldCheck,
  Store,
  Users,
  Warehouse,
  X,
  Zap,
} from 'lucide-react'
import ThemeToggle from '@/components/ThemeToggle'
import { useAuth } from '@/contexts/AuthContext'
import type { Role } from '@/types'

interface NavItem {
  href: string
  label: string
  icon: typeof LayoutDashboard
  /** Bỏ trống = mọi vai trò vào được khu quản trị (ADMIN và STAFF). */
  roles?: Role[]
}

const NAV: NavItem[] = [
  { href: '/admin', label: 'Tổng quan', icon: LayoutDashboard, roles: ['ADMIN'] },
  { href: '/admin/analytics', label: 'Thống kê', icon: BarChart3, roles: ['ADMIN'] },
  { href: '/admin/orders', label: 'Đơn hàng', icon: Package },
  { href: '/admin/products', label: 'Sản phẩm', icon: Boxes },
  { href: '/admin/inventory', label: 'Tồn kho', icon: Warehouse },
  { href: '/admin/serials', label: 'Serial / IMEI', icon: ScanBarcode },
  { href: '/admin/warranty', label: 'Bảo hành', icon: ShieldCheck },
  { href: '/admin/coupons', label: 'Mã giảm giá', icon: BadgePercent, roles: ['ADMIN'] },
  { href: '/admin/flash-sales', label: 'Flash sale', icon: Zap, roles: ['ADMIN'] },
  { href: '/admin/users', label: 'Người dùng', icon: Users, roles: ['ADMIN'] },
  { href: '/admin/audit-logs', label: 'Nhật ký thao tác', icon: ClipboardList, roles: ['ADMIN'] },
]

/**
 * Khung khu quản trị: thanh bên cố định trên desktop, ngăn kéo trên màn hình hẹp.
 *
 * Mục nào STAFF không có quyền thì ẩn hẳn — để hiện rồi trả 403 chỉ làm người
 * dùng đoán mò. Backend vẫn là nơi chặn thật.
 */
export default function AdminShell({ children }: { children: React.ReactNode }) {
  const pathname = usePathname()
  const { user, logout } = useAuth()
  const [open, setOpen] = useState(false)

  const items = NAV.filter((it) => !it.roles || (user && it.roles.includes(user.role)))

  const sidebar = (
    <div className="flex flex-col h-full">
      <div className="flex items-center gap-2 px-4 h-14 border-b border-slate-200 dark:border-surface-border shrink-0">
        <span className="w-8 h-8 rounded-lg bg-primary-600 dark:bg-primary-500 text-white flex items-center justify-center">
          <Cpu size={17} />
        </span>
        <div className="min-w-0">
          <p className="font-extrabold text-sm text-slate-800 dark:text-slate-100 leading-tight">
            TechMart
          </p>
          <p className="text-2xs text-slate-500 dark:text-slate-400 leading-tight">Quản trị</p>
        </div>
      </div>

      <nav className="flex-1 overflow-y-auto p-2.5 flex flex-col gap-0.5">
        {items.map((item) => {
          const Icon = item.icon
          // '/admin' khớp chính xác, các mục con khớp theo tiền tố.
          const active = item.href === '/admin' ? pathname === '/admin' : pathname.startsWith(item.href)
          return (
            <Link
              key={item.href}
              href={item.href}
              onClick={() => setOpen(false)}
              className={`nav-item ${active ? 'nav-item-active' : 'nav-item-idle'}`}
            >
              <Icon size={16} />
              {item.label}
            </Link>
          )
        })}
      </nav>

      <div className="p-2.5 border-t border-slate-200 dark:border-surface-border shrink-0">
        {user && (
          <div className="px-2.5 pb-2">
            <p className="text-sm font-bold text-slate-700 dark:text-slate-200 truncate">
              {user.fullName}
            </p>
            <span className="badge badge-indigo mt-1">{user.role}</span>
          </div>
        )}
        <Link href="/" className="nav-item nav-item-idle">
          <Store size={16} /> Về cửa hàng
        </Link>
        <button
          onClick={() => void logout()}
          className="nav-item nav-item-idle text-red-600 dark:text-red-400 hover:bg-red-50 dark:hover:bg-red-900/20"
        >
          <LogOut size={16} /> Đăng xuất
        </button>
      </div>
    </div>
  )

  return (
    <div className="min-h-screen flex">
      {/* Thanh bên cố định — desktop */}
      <aside className="hidden lg:flex w-60 shrink-0 flex-col bg-white dark:bg-surface-card border-r border-slate-200 dark:border-surface-border sticky top-0 h-screen">
        {sidebar}
      </aside>

      {/* Ngăn kéo — màn hình hẹp */}
      {open && (
        <div className="lg:hidden fixed inset-0 z-50 flex">
          <div className="absolute inset-0 bg-slate-900/50 backdrop-blur-sm" onClick={() => setOpen(false)} />
          <aside className="relative w-64 bg-white dark:bg-surface-card border-r border-slate-200 dark:border-surface-border animate-fade-in-down">
            {sidebar}
          </aside>
        </div>
      )}

      <div className="flex-1 min-w-0 flex flex-col">
        <header className="lg:hidden sticky top-0 z-30 flex items-center gap-2 px-4 h-14 bg-white/95 dark:bg-surface-card/95 backdrop-blur border-b border-slate-200 dark:border-surface-border">
          <button
            onClick={() => setOpen(true)}
            className="btn-ghost p-1.5 rounded-lg"
            aria-label="Mở menu quản trị"
          >
            <Menu size={18} />
          </button>
          <span className="font-bold text-sm text-slate-800 dark:text-slate-100">Quản trị</span>
          <div className="ml-auto">
            <ThemeToggle />
          </div>
        </header>

        <div className="hidden lg:flex items-center justify-end px-6 h-14 border-b border-slate-200 dark:border-surface-border bg-white/60 dark:bg-surface-card/40">
          <ThemeToggle />
        </div>

        <main className="flex-1 p-4 sm:p-6 min-w-0">{children}</main>
      </div>

      {/* Nút đóng nổi để đóng ngăn kéo bằng bàn phím */}
      {open && (
        <button
          onClick={() => setOpen(false)}
          className="lg:hidden fixed top-3 right-3 z-[60] btn-ghost p-2 rounded-lg bg-white dark:bg-surface-card"
          aria-label="Đóng menu"
        >
          <X size={18} />
        </button>
      )}
    </div>
  )
}
