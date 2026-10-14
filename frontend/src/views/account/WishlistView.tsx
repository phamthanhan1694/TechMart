'use client'

import { useEffect, useState } from 'react'
import Link from 'next/link'
import { Heart, Trash2 } from 'lucide-react'
import * as engagementApi from '@/lib/api/engagement'
import ProductImage from '@/components/ProductImage'
import { EmptyState, Loading } from '@/components/Feedback'
import { useWishlist } from '@/contexts/WishlistContext'
import { useToast } from '@/contexts/ToastContext'
import { formatPrice, formatRelative } from '@/lib/format'
import type { WishlistItem } from '@/types'

export default function WishlistView() {
  const wishlist = useWishlist()
  const toast = useToast()
  const [items, setItems] = useState<WishlistItem[] | null>(null)

  useEffect(() => {
    engagementApi
      .wishlist()
      .then(setItems)
      .catch(() => setItems([]))
  }, [])

  const remove = async (item: WishlistItem) => {
    try {
      if (wishlist.has(item.productId)) await wishlist.toggle(item.productId)
      else await engagementApi.removeFromWishlist(item.productId)
      setItems((list) => list?.filter((x) => x.productId !== item.productId) ?? null)
    } catch (err) {
      toast.showError(err, 'Không bỏ được sản phẩm')
    }
  }

  if (items == null) return <Loading />

  return (
    <div className="flex flex-col gap-4">
      <div>
        <h1 className="page-title">Yêu thích</h1>
        <p className="page-desc">{items.length} sản phẩm · tối đa 100</p>
      </div>

      {items.length === 0 ? (
        <div className="card">
          <EmptyState
            icon={Heart}
            title="Chưa có sản phẩm yêu thích"
            desc="Bấm biểu tượng trái tim trên sản phẩm để lưu lại xem sau."
            action={<Link href="/products" className="btn btn-primary">Xem sản phẩm</Link>}
          />
        </div>
      ) : (
        <ul className="grid gap-3 grid-cols-2 md:grid-cols-3 lg:grid-cols-4">
          {items.map((item) => (
            <li key={item.productId} className="card p-3 flex flex-col gap-2">
              <Link href={`/products/${item.slug}`} className={item.purchasable ? '' : 'opacity-50'}>
                <ProductImage src={item.thumbnailUrl} alt={item.name} />
              </Link>
              <p className="text-2xs text-slate-500 dark:text-slate-400 font-semibold">{item.brandName}</p>
              <Link href={`/products/${item.slug}`} className="font-semibold text-sm clamp-2 text-slate-800 dark:text-slate-100 hover:text-primary-600 dark:hover:text-primary-400">
                {item.name}
              </Link>
              <div className="mt-auto flex items-end justify-between gap-2">
                <div>
                  {item.purchasable ? (
                    <>
                      <p className="price text-[0.95rem]">{formatPrice(item.minPrice)}</p>
                      <p className={`text-2xs font-semibold ${item.availableQuantity > 0 ? 'text-emerald-600 dark:text-emerald-400' : 'text-slate-400'}`}>
                        {item.availableQuantity > 0 ? 'Còn hàng' : 'Tạm hết hàng'}
                      </p>
                    </>
                  ) : (
                    <span className="badge badge-slate">Ngừng kinh doanh</span>
                  )}
                  <p className="text-[10px] text-slate-400 dark:text-slate-500 mt-0.5">Lưu {formatRelative(item.addedAt)}</p>
                </div>
                <button onClick={() => void remove(item)} className="btn btn-ghost p-1.5 rounded-lg" aria-label={`Bỏ ${item.name} khỏi yêu thích`}>
                  <Trash2 size={14} />
                </button>
              </div>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}
