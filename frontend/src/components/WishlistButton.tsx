'use client'

import { useState } from 'react'
import { Heart } from 'lucide-react'
import { useWishlist } from '@/contexts/WishlistContext'
import { useToast } from '@/contexts/ToastContext'

/** Nút tim. Ẩn hẳn với khách chưa đăng nhập và với STAFF/ADMIN - họ không có danh sách yêu thích. */
export default function WishlistButton({
  productId,
  productName,
  variant = 'icon',
}: {
  productId: number
  productName: string
  variant?: 'icon' | 'full'
}) {
  const wishlist = useWishlist()
  const toast = useToast()
  const [busy, setBusy] = useState(false)

  if (!wishlist.enabled) return null
  const active = wishlist.has(productId)

  const onClick = async (e: React.MouseEvent) => {
    e.preventDefault()
    e.stopPropagation()
    setBusy(true)
    try {
      const added = await wishlist.toggle(productId)
      toast.success(added ? `Đã thêm ${productName} vào yêu thích` : 'Đã bỏ khỏi yêu thích')
    } catch (err) {
      toast.showError(err, 'Không cập nhật được danh sách yêu thích')
    } finally {
      setBusy(false)
    }
  }

  const label = active ? 'Bỏ khỏi yêu thích' : 'Thêm vào yêu thích'

  if (variant === 'full') {
    return (
      <button onClick={onClick} disabled={busy} aria-pressed={active} className="btn btn-secondary w-full">
        <Heart size={15} className={active ? 'fill-primary-600 text-primary-600 dark:fill-primary-400 dark:text-primary-400' : ''} />
        {active ? 'Đã yêu thích' : 'Yêu thích'}
      </button>
    )
  }

  return (
    <button
      onClick={onClick}
      disabled={busy}
      aria-pressed={active}
      aria-label={label}
      title={label}
      className={`p-1.5 rounded-lg border transition-colors ${
        active
          ? 'bg-primary-50 border-primary-200 text-primary-600 dark:bg-primary-900/30 dark:border-primary-800 dark:text-primary-400'
          : 'bg-white/90 border-slate-200 text-slate-400 hover:text-primary-600 dark:bg-slate-800/90 dark:border-slate-600'
      }`}
    >
      <Heart size={13} className={active ? 'fill-current' : ''} />
    </button>
  )
}
