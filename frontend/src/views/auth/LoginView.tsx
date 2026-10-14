'use client'

import { useEffect, useState } from 'react'
import Link from 'next/link'
import { useRouter, useSearchParams } from 'next/navigation'
import { AlertCircle, Cpu, Eye, EyeOff, Loader2 } from 'lucide-react'
import * as authApi from '@/lib/api/auth'
import { getToken, saveTokens } from '@/lib/auth'
import { ApiError } from '@/lib/api/client'
import { useAuth } from '@/contexts/AuthContext'
import ThemeToggle from '@/components/ThemeToggle'

/** Tài khoản có sẵn trong seed (README mục "Tài khoản demo"). */
const DEMO_ACCOUNTS = [
  { label: 'Khách hàng', email: 'customer@techmart.dev', password: 'Customer@123' },
  { label: 'Nhân viên', email: 'staff@techmart.dev', password: 'Staff@123' },
  { label: 'Quản trị', email: 'admin@techmart.dev', password: 'Admin@123' },
]

export default function LoginView() {
  const router = useRouter()
  const searchParams = useSearchParams()
  const { login } = useAuth()

  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [showPwd, setShowPwd] = useState(false)
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)

  // Quay lại đúng trang đã bị chặn, mặc định về trang chủ.
  const next = searchParams.get('next') || '/'

  useEffect(() => {
    if (getToken()) router.replace(next)
  }, [router, next])

  const submit = async (e: React.FormEvent, creds?: { email: string; password: string }) => {
    e.preventDefault()
    setError('')
    setLoading(true)
    try {
      const mail = creds?.email ?? email.trim()
      const pwd = creds?.password ?? password
      const tokens = await authApi.login(mail, pwd)
      // /auth/login chỉ trả cặp token; hồ sơ người dùng nằm ở /auth/me.
      // Lưu token trước để lời gọi /auth/me ngay sau đó có Authorization header.
      saveTokens(tokens.accessToken, tokens.refreshToken)
      const user = await authApi.me()
      login(tokens.accessToken, tokens.refreshToken, user)
      router.replace(user.role === 'CUSTOMER' ? next : '/admin/orders')
    } catch (err) {
      // Sai email và sai mật khẩu đều trả INVALID_CREDENTIALS — backend cố ý
      // không tiết lộ email nào có thật, nên ở đây cũng không đoán thêm.
      setError(err instanceof ApiError ? err.message : 'Đăng nhập thất bại')
      setLoading(false)
    }
  }

  const quickLogin = (acc: (typeof DEMO_ACCOUNTS)[number]) => {
    setEmail(acc.email)
    setPassword(acc.password)
    void submit({ preventDefault: () => {} } as React.FormEvent, acc)
  }

  return (
    <div className="min-h-screen bg-gradient-to-br from-slate-50 via-white to-primary-50 dark:from-slate-900 dark:via-slate-900 dark:to-primary-950 flex items-center justify-center p-4">
      <div className="fixed top-4 right-4">
        <ThemeToggle />
      </div>

      <div className="w-full max-w-sm animate-fade-in-down">
        <div className="text-center mb-8">
          <div className="w-14 h-14 bg-primary-600 dark:bg-primary-500 rounded-2xl flex items-center justify-center text-white mx-auto mb-3 shadow-lg shadow-primary-200 dark:shadow-primary-900/40">
            <Cpu size={26} />
          </div>
          <h1 className="text-xl font-extrabold text-slate-800 dark:text-slate-100">TechMart</h1>
          <p className="text-sm text-slate-500 dark:text-slate-400 mt-0.5">
            Điện thoại &amp; laptop chính hãng
          </p>
        </div>

        <div className="card p-6">
          <h2 className="text-lg font-bold text-slate-800 dark:text-slate-100 mb-5">Đăng nhập</h2>

          <form onSubmit={submit} className="space-y-4">
            <div>
              <label className="form-label" htmlFor="login-email">
                Email
              </label>
              <input
                id="login-email"
                type="email"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                required
                autoFocus
                autoComplete="email"
                placeholder="ban@email.com"
                className="form-input"
              />
            </div>

            <div>
              <label className="form-label" htmlFor="login-password">
                Mật khẩu
              </label>
              <div className="relative">
                <input
                  id="login-password"
                  type={showPwd ? 'text' : 'password'}
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  required
                  autoComplete="current-password"
                  placeholder="••••••••"
                  className="form-input pr-10"
                />
                <button
                  type="button"
                  onClick={() => setShowPwd((v) => !v)}
                  className="absolute right-3 top-1/2 -translate-y-1/2 text-slate-400 hover:text-slate-600 dark:hover:text-slate-300 leading-none transition-colors"
                  aria-label={showPwd ? 'Ẩn mật khẩu' : 'Hiện mật khẩu'}
                >
                  {showPwd ? <EyeOff size={16} /> : <Eye size={16} />}
                </button>
              </div>
            </div>

            {error && (
              <div className="form-error">
                <AlertCircle size={15} className="shrink-0 mt-0.5" />
                <span>{error}</span>
              </div>
            )}

            <button type="submit" disabled={loading} className="btn btn-primary w-full py-2.5 mt-1">
              {loading ? (
                <>
                  <Loader2 size={16} className="animate-spin" />
                  Đang đăng nhập…
                </>
              ) : (
                'Đăng nhập'
              )}
            </button>
          </form>

          <div className="mt-5 pt-4 border-t border-slate-100 dark:border-slate-700">
            <p className="section-label mb-2">Tài khoản demo</p>
            <div className="grid grid-cols-3 gap-1.5">
              {DEMO_ACCOUNTS.map((acc) => (
                <button
                  key={acc.email}
                  type="button"
                  disabled={loading}
                  onClick={() => quickLogin(acc)}
                  className="btn btn-secondary btn-sm justify-center"
                >
                  {acc.label}
                </button>
              ))}
            </div>
          </div>
        </div>

        <p className="text-center text-sm text-slate-500 dark:text-slate-400 mt-4">
          Chưa có tài khoản?{' '}
          <Link
            href="/register"
            className="text-primary-600 dark:text-primary-400 font-semibold hover:underline underline-offset-2"
          >
            Đăng ký ngay
          </Link>
        </p>
      </div>
    </div>
  )
}
