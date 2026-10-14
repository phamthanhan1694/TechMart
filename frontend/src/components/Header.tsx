'use client'

import { useEffect, useRef, useState } from 'react'
import Link from 'next/link'
import { usePathname, useRouter, useSearchParams } from 'next/navigation'
import {
  ChevronDown,
  Cpu,
  Heart,
  LayoutDashboard,
  LogOut,
  Menu,
  Package,
  Search,
  ShieldCheck,
  ShoppingCart,
  User,
  UserCog,
  X,
  Zap,
} from 'lucide-react'
import ThemeToggle from '@/components/ThemeToggle'
import NotificationBell from '@/components/NotificationBell'
import { useAuth } from '@/contexts/AuthContext'
import { useCart } from '@/contexts/CartContext'
import { isStaff } from '@/lib/auth'

const NAV = [
  { href: '/products', label: 'Sản phẩm', icon: Package },
  { href: '/flash-sales', label: 'Flash sale', icon: Zap },
  { href: '/warranty', label: 'Bảo hành', icon: ShieldCheck },
]

export default function Header() {
  const pathname = usePathname()
  const router = useRouter()
  const searchParams = useSearchParams()
  const { user, logout } = useAuth()
  const { count } = useCart()

  const [keyword, setKeyword] = useState(searchParams.get('keyword') ?? '')
  const [menuOpen, setMenuOpen] = useState(false)
  const [mobileOpen, setMobileOpen] = useState(false)
  const menuRef = useRef<HTMLDivElement>(null)

  // Ô tìm kiếm phải theo kịp URL: bấm nút lùi của trình duyệt mà ô vẫn giữ từ khoá cũ là sai.
  useEffect(() => {
    setKeyword(searchParams.get('keyword') ?? '')
  }, [searchParams])

  useEffect(() => {
    setMobileOpen(false)
    setMenuOpen(false)
  }, [pathname])

  useEffect(() => {
    if (!menuOpen) return
    const onClick = (e: MouseEvent) => {
      if (menuRef.current && !menuRef.current.contains(e.target as Node)) setMenuOpen(false)
    }
    document.addEventListener('mousedown', onClick)
    return () => document.removeEventListener('mousedown', onClick)
  }, [menuOpen])

  const submitSearch = (e: React.FormEvent) => {
    e.preventDefault()
    const q = keyword.trim()
    router.push(q ? `/products?keyword=${encodeURIComponent(q)}` : '/products')
  }

  return (
    <header className="sticky top-0 z-40 bg-white/95 dark:bg-surface-card/95 backdrop-blur border-b border-slate-200 dark:border-surface-border">
      <div className="mx-auto max-w-6xl px-4">
        <div className="flex items-center gap-3 h-14">
          <button
            className="btn-ghost p-1.5 rounded-lg md:hidden"
            onClick={() => setMobileOpen((v) => !v)}
            aria-label="Mở menu"
            aria-expanded={mobileOpen}
          >
            {mobileOpen ? <X size={18} /> : <Menu size={18} />}
          </button>

          <Link href="/" className="flex items-center gap-2 shrink-0">
            <span className="w-8 h-8 rounded-lg bg-primary-600 dark:bg-primary-500 text-white flex items-center justify-center">
              <Cpu size={17} />
            </span>
            <span className="font-extrabold text-base text-slate-800 dark:text-slate-100 hidden sm:block">
              TechMart
            </span>
          </Link>

          <nav className="hidden md:flex items-center gap-0.5">
            {NAV.map((item) => {
              const active = pathname.startsWith(item.href)
              return (
                <Link
                  key={item.href}
                  href={item.href}
                  className={`px-3 py-1.5 rounded-lg text-sm font-semibold transition-colors ${
                    active
                      ? 'text-primary-700 bg-primary-50 dark:text-primary-400 dark:bg-primary-900/30'
                      : 'text-slate-600 hover:text-slate-900 hover:bg-slate-100 dark:text-slate-300 dark:hover:text-slate-100 dark:hover:bg-slate-700/60'
                  }`}
                >
                  {item.label}
                </Link>
              )
            })}
          </nav>

          <form onSubmit={submitSearch} className="flex-1 max-w-md ml-auto hidden sm:block">
            <div className="relative">
              <Search
                size={15}
                className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400 pointer-events-none"
              />
              <input
                value={keyword}
                onChange={(e) => setKeyword(e.target.value)}
                placeholder="Tìm iPhone, MacBook, Galaxy…"
                aria-label="Tìm sản phẩm"
                className="form-input pl-9 py-2 text-sm"
              />
            </div>
          </form>

          <div className="flex items-center gap-1 ml-auto sm:ml-0">
            <ThemeToggle />

            {/* Hiện chỉ khách hàng nhận thông báo (vòng đời đơn) - không polling vô ích cho nhân viên */}
            {user?.role === 'CUSTOMER' && <NotificationBell />}

            {user?.role === 'CUSTOMER' && (
              <Link
                href="/cart"
                className="relative btn-ghost p-2 rounded-lg"
                aria-label={`Giỏ hàng, ${count} sản phẩm`}
              >
                <ShoppingCart size={17} />
                {count > 0 && (
                  <span className="absolute -top-0.5 -right-0.5 min-w-4 h-4 px-1 rounded-full bg-primary-600 text-white text-[10px] font-bold flex items-center justify-center">
                    {count > 99 ? '99+' : count}
                  </span>
                )}
              </Link>
            )}

            {user ? (
              <div className="relative" ref={menuRef}>
                <button
                  onClick={() => setMenuOpen((v) => !v)}
                  className="flex items-center gap-1.5 px-2 py-1.5 rounded-lg text-sm font-semibold text-slate-600 hover:bg-slate-100 dark:text-slate-300 dark:hover:bg-slate-700/60 transition-colors"
                  aria-expanded={menuOpen}
                >
                  <span className="w-6 h-6 rounded-full bg-primary-100 dark:bg-primary-900/50 text-primary-700 dark:text-primary-300 flex items-center justify-center text-2xs font-bold">
                    {user.fullName?.trim().charAt(0).toUpperCase() || 'U'}
                  </span>
                  <span className="hidden lg:block max-w-24 truncate">{user.fullName}</span>
                  <ChevronDown size={13} className="hidden lg:block" />
                </button>

                {menuOpen && (
                  <div className="absolute right-0 top-full mt-1.5 w-56 card p-1.5 shadow-lg animate-fade-in-down">
                    <div className="px-2.5 py-2 border-b border-slate-100 dark:border-slate-700 mb-1">
                      <p className="text-sm font-bold text-slate-800 dark:text-slate-100 truncate">
                        {user.fullName}
                      </p>
                      <p className="text-2xs text-slate-500 dark:text-slate-400 truncate">
                        {user.email}
                      </p>
                      <span className="badge badge-indigo mt-1.5">{user.role}</span>
                    </div>

                    {user.role === 'CUSTOMER' && (
                      <>
                        <Link href="/orders" className="nav-item nav-item-idle text-sm">
                          <Package size={15} /> Đơn hàng của tôi
                        </Link>
                        <Link href="/wishlist" className="nav-item nav-item-idle text-sm">
                          <Heart size={15} /> Yêu thích
                        </Link>
                      </>
                    )}

                    <Link href="/account" className="nav-item nav-item-idle text-sm">
                      <UserCog size={15} /> Tài khoản
                    </Link>

                    {isStaff(user) && (
                      <Link href="/admin" className="nav-item nav-item-idle text-sm">
                        <LayoutDashboard size={15} /> Khu quản trị
                      </Link>
                    )}

                    <button
                      onClick={() => void logout()}
                      className="nav-item nav-item-idle text-sm text-red-600 dark:text-red-400 hover:bg-red-50 dark:hover:bg-red-900/20"
                    >
                      <LogOut size={15} /> Đăng xuất
                    </button>
                  </div>
                )}
              </div>
            ) : (
              <Link href="/login" className="btn btn-primary btn-sm ml-1">
                <User size={14} /> Đăng nhập
              </Link>
            )}
          </div>
        </div>

        {/* Menu và ô tìm kiếm cho màn hình hẹp */}
        {mobileOpen && (
          <div className="md:hidden pb-3 animate-fade-in-down">
            <form onSubmit={submitSearch} className="sm:hidden mb-2">
              <div className="relative">
                <Search
                  size={15}
                  className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400 pointer-events-none"
                />
                <input
                  value={keyword}
                  onChange={(e) => setKeyword(e.target.value)}
                  placeholder="Tìm sản phẩm…"
                  aria-label="Tìm sản phẩm"
                  className="form-input pl-9 py-2 text-sm"
                />
              </div>
            </form>
            <nav className="flex flex-col gap-0.5">
              {NAV.map((item) => {
                const Icon = item.icon
                const active = pathname.startsWith(item.href)
                return (
                  <Link
                    key={item.href}
                    href={item.href}
                    className={`nav-item ${active ? 'nav-item-active' : 'nav-item-idle'}`}
                  >
                    <Icon size={15} /> {item.label}
                  </Link>
                )
              })}
            </nav>
          </div>
        )}
      </div>
    </header>
  )
}
