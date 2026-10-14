'use client'

import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useState,
  type ReactNode,
} from 'react'
import * as authApi from '@/lib/api/auth'
import {
  clearAuth,
  getToken,
  getUser,
  saveAuth,
  saveUser,
  type AuthUser,
} from '@/lib/auth'

interface AuthContextType {
  user: AuthUser | null
  isLoading: boolean
  login: (accessToken: string, refreshToken: string, user: AuthUser) => void
  logout: () => Promise<void>
  /** Nap lai /auth/me - dung sau khi doi thong tin hoac de kiem tra token con song. */
  reloadUser: () => Promise<void>
}

const AuthContext = createContext<AuthContextType | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<AuthUser | null>(null)
  const [isLoading, setIsLoading] = useState(true)

  useEffect(() => {
    // Hien user da luu ngay lap tuc de khong nhay giao dien, roi doi chieu voi
    // server o nen. Token het han se duoc client.ts tu xoay vong.
    const saved = getUser()
    if (getToken() && saved) {
      setUser(saved)
      authApi
        .me()
        .then((fresh) => {
          setUser(fresh)
          saveUser(fresh)
        })
        .catch(() => {
          // 401 da duoc client.ts xu ly (xoay vong hoac dua ve /login).
        })
    }
    setIsLoading(false)
  }, [])

  const login = useCallback((accessToken: string, refreshToken: string, u: AuthUser) => {
    saveAuth(accessToken, refreshToken, u)
    setUser(u)
  }, [])

  const logout = useCallback(async () => {
    // Thu hoi refresh token o server truoc; that bai cung van xoa phien o client.
    await authApi.logout().catch(() => undefined)
    clearAuth()
    setUser(null)
    // Nạp lại cả trang thay vì router.push: đăng xuất phải xoá sạch mọi state
    // còn trong bộ nhớ (giỏ hàng, danh sách đơn), không chỉ đổi route.
    // Dùng URL tuyệt đối — gán chuỗi tương đối vào location là nhập nhằng.
    window.location.assign(new URL('/login', window.location.origin))
  }, [])

  const reloadUser = useCallback(async () => {
    const fresh = await authApi.me()
    setUser(fresh)
    saveUser(fresh)
  }, [])

  return (
    <AuthContext.Provider value={{ user, isLoading, login, logout, reloadUser }}>
      {children}
    </AuthContext.Provider>
  )
}

export function useAuth(): AuthContextType {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be used inside <AuthProvider>')
  return ctx
}
