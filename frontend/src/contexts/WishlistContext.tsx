'use client'

import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from 'react'
import * as engagementApi from '@/lib/api/engagement'
import { useAuth } from '@/contexts/AuthContext'

interface WishlistContextType {
  /** Có dùng được không - chỉ khách hàng mới có danh sách yêu thích. */
  enabled: boolean
  has: (productId: number) => boolean
  /** Trả về trạng thái MỚI (true = vừa thêm). Ném lỗi nếu backend từ chối. */
  toggle: (productId: number) => Promise<boolean>
  count: number
}

const WishlistContext = createContext<WishlistContextType | null>(null)

/**
 * Tập id sản phẩm yêu thích, nạp MỘT lần qua /wishlist/ids để mọi thẻ sản phẩm tô tim
 * đúng mà không phải hỏi backend từng thẻ.
 *
 * Cập nhật lạc quan: tim đổi màu ngay khi bấm, lỗi thì trả lại như cũ. An toàn vì PUT và
 * DELETE ở backend đều idempotent - bấm nhanh hai lần cũng không sinh trạng thái lạ.
 */
export function WishlistProvider({ children }: { children: ReactNode }) {
  const { user } = useAuth()
  const enabled = user?.role === 'CUSTOMER'
  const [ids, setIds] = useState<Set<number>>(new Set())

  useEffect(() => {
    if (!enabled) {
      setIds(new Set())
      return
    }
    let cancelled = false
    engagementApi
      .wishlistIds()
      .then((list) => !cancelled && setIds(new Set(list)))
      .catch(() => undefined)
    return () => {
      cancelled = true
    }
  }, [enabled])

  const toggle = useCallback(
    async (productId: number) => {
      const adding = !ids.has(productId)
      const apply = (add: boolean) =>
        setIds((prev) => {
          const next = new Set(prev)
          if (add) next.add(productId)
          else next.delete(productId)
          return next
        })

      apply(adding)
      try {
        if (adding) await engagementApi.addToWishlist(productId)
        else await engagementApi.removeFromWishlist(productId)
        return adding
      } catch (err) {
        apply(!adding)
        throw err
      }
    },
    [ids],
  )

  const value = useMemo(
    () => ({ enabled, has: (id: number) => ids.has(id), toggle, count: ids.size }),
    [enabled, ids, toggle],
  )

  return <WishlistContext.Provider value={value}>{children}</WishlistContext.Provider>
}

export function useWishlist(): WishlistContextType {
  const ctx = useContext(WishlistContext)
  if (!ctx) throw new Error('useWishlist must be used inside <WishlistProvider>')
  return ctx
}
