'use client'

import { useEffect, useState } from 'react'
import Link from 'next/link'
import { useRouter } from 'next/navigation'
import { AlertCircle, Cpu, Eye, EyeOff, Loader2 } from 'lucide-react'
import * as authApi from '@/lib/api/auth'
import { ApiError } from '@/lib/api/client'
import { getToken, saveTokens } from '@/lib/auth'
import { useAuth } from '@/contexts/AuthContext'
import ThemeToggle from '@/components/ThemeToggle'

export default function RegisterView() {
  const router = useRouter()
  const { login } = useAuth()

  const [fullName, setFullName] = useState('')
  const [email, setEmail] = useState('')
  const [phone, setPhone] = useState('')
  const [password, setPassword] = useState('')
  const [showPwd, setShowPwd] = useState(false)
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)

  useEffect(() => {
    if (getToken()) router.replace('/')
  }, [router])

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    setError('')

    // Chặn sớm hai luật backend sẽ từ chối, để khỏi mất một vòng gọi mạng.
    if (password.length < 8) {
      setError('Mật khẩu phải có ít nhất 8 ký tự')
      return
    }
    if (phone.trim() && !/^0\d{9}$/.test(phone.trim())) {
      setError('Số điện thoại phải có 10 chữ số và bắt đầu bằng 0')
      return
    }

    setLoading(true)
    try {
      await authApi.register({
        email: email.trim(),
        password,
        fullName: fullName.trim(),
        phone: phone.trim(),
      })
      // Đăng ký xong đăng nhập luôn — backend luôn tạo tài khoản CUSTOMER.
      const tokens = await authApi.login(email.trim(), password)
      saveTokens(tokens.accessToken, tokens.refreshToken)
      const user = await authApi.me()
      login(tokens.accessToken, tokens.refreshToken, user)
      router.replace('/')
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Đăng ký thất bại')
      setLoading(false)
    }
  }

  return (
    <div className="min-h-screen bg-gradient-to-br from-slate-50 via-white to-primary-50 dark:from-slate-900 dark:via-slate-900 dark:to-primary-950 flex items-center justify-center p-4">
      <div className="fixed top-4 right-4">
        <ThemeToggle />
      </div>

      <div className="w-full max-w-sm animate-fade-in-down py-8">
        <div className="text-center mb-6">
          <div className="w-14 h-14 bg-primary-600 dark:bg-primary-500 rounded-2xl flex items-center justify-center text-white mx-auto mb-3 shadow-lg shadow-primary-200 dark:shadow-primary-900/40">
            <Cpu size={26} />
          </div>
          <h1 className="text-xl font-extrabold text-slate-800 dark:text-slate-100">TechMart</h1>
          <p className="text-sm text-slate-500 dark:text-slate-400 mt-0.5">Tạo tài khoản mới</p>
        </div>

        <div className="card p-6">
          <h2 className="text-lg font-bold text-slate-800 dark:text-slate-100 mb-5">Đăng ký</h2>

          <form onSubmit={handleSubmit} className="space-y-4">
            <div>
              <label className="form-label" htmlFor="reg-name">
                Họ và tên
              </label>
              <input
                id="reg-name"
                value={fullName}
                onChange={(e) => setFullName(e.target.value)}
                required
                autoFocus
                maxLength={150}
                autoComplete="name"
                placeholder="Nguyễn Văn A"
                className="form-input"
              />
            </div>

            <div>
              <label className="form-label" htmlFor="reg-email">
                Email
              </label>
              <input
                id="reg-email"
                type="email"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                required
                autoComplete="email"
                placeholder="ban@email.com"
                className="form-input"
              />
            </div>

            <div>
              <label className="form-label" htmlFor="reg-phone">
                Số điện thoại
              </label>
              <input
                id="reg-phone"
                type="tel"
                value={phone}
                onChange={(e) => setPhone(e.target.value)}
                autoComplete="tel"
                placeholder="0901234567"
                className="form-input"
              />
              <p className="form-hint">Không bắt buộc. 10 chữ số, bắt đầu bằng 0.</p>
            </div>

            <div>
              <label className="form-label" htmlFor="reg-password">
                Mật khẩu
              </label>
              <div className="relative">
                <input
                  id="reg-password"
                  type={showPwd ? 'text' : 'password'}
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  required
                  minLength={8}
                  maxLength={72}
                  autoComplete="new-password"
                  placeholder="Tối thiểu 8 ký tự"
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
                  Đang tạo tài khoản…
                </>
              ) : (
                'Đăng ký'
              )}
            </button>
          </form>
        </div>

        <p className="text-center text-sm text-slate-500 dark:text-slate-400 mt-4">
          Đã có tài khoản?{' '}
          <Link
            href="/login"
            className="text-primary-600 dark:text-primary-400 font-semibold hover:underline underline-offset-2"
          >
            Đăng nhập
          </Link>
        </p>
      </div>
    </div>
  )
}
