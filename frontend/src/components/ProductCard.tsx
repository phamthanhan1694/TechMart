'use client'

import Link from 'next/link'
import { GitCompareArrows } from 'lucide-react'
import ProductImage from '@/components/ProductImage'
import WishlistButton from '@/components/WishlistButton'
import { formatPrice, specLabel, specValue } from '@/lib/format'
import type { ProductSummaryResponse } from '@/types'

interface Props {
  product: ProductSummaryResponse
  /** Bật nút thêm vào danh sách so sánh (trang /products). */
  onToggleCompare?: (id: number) => void
  inCompare?: boolean
  priority?: boolean
}

/** Vài thông số đáng liếc nhanh ngay trên thẻ, đúng thứ tự này. */
const HIGHLIGHT_SPECS = ['chipset', 'cpu', 'ram', 'storage', 'screen'] as const

export default function ProductCard({ product, onToggleCompare, inCompare, priority }: Props) {
  const spec = product.spec ?? {}
  const highlights = HIGHLIGHT_SPECS.filter((k) => spec[k]).slice(0, 3)
  const soldOut = product.availableQuantity <= 0
  // minPrice === maxPrice khi sản phẩm chỉ có một phiên bản.
  const priceRange = product.minPrice !== product.maxPrice

  return (
    <div className="card p-3 flex flex-col gap-2.5 transition-shadow hover:shadow-md group relative">
      {onToggleCompare && (
        <button
          onClick={() => onToggleCompare(product.id)}
          title={inCompare ? 'Bỏ khỏi so sánh' : 'Thêm vào so sánh'}
          aria-pressed={inCompare}
          className={`absolute top-2 right-2 z-10 p-1.5 rounded-lg border transition-all ${
            inCompare
              ? 'bg-primary-600 border-primary-600 text-white'
              : 'bg-white/90 border-slate-200 text-slate-400 opacity-0 group-hover:opacity-100 focus-visible:opacity-100 hover:text-primary-600 dark:bg-slate-800/90 dark:border-slate-600 dark:text-slate-400'
          }`}
        >
          <GitCompareArrows size={13} />
        </button>
      )}

      <div className="absolute top-2 left-2 z-10">
        <WishlistButton productId={product.id} productName={product.name} />
      </div>

      <Link href={`/products/${product.slug}`} className="block">
        <ProductImage src={product.thumbnailUrl} alt={product.name} priority={priority} />
      </Link>

      <div className="flex flex-col gap-1.5 flex-1">
        <p className="text-2xs text-slate-500 dark:text-slate-400 font-semibold">
          {product.brandName}
        </p>

        <Link
          href={`/products/${product.slug}`}
          className="font-semibold text-sm leading-snug clamp-2 text-slate-800 dark:text-slate-100 hover:text-primary-600 dark:hover:text-primary-400 transition-colors"
        >
          {product.name}
        </Link>

        {highlights.length > 0 && (
          <ul className="flex flex-wrap gap-1">
            {highlights.map((k) => (
              <li
                key={k}
                className="text-2xs px-1.5 py-0.5 rounded bg-slate-100 text-slate-600 dark:bg-slate-700/50 dark:text-slate-300"
                title={specLabel(k)}
              >
                {specValue(spec[k])}
              </li>
            ))}
          </ul>
        )}

        <div className="mt-auto pt-1">
          <p className="price text-[0.95rem]">
            {formatPrice(product.minPrice)}
            {priceRange && (
              <span className="text-2xs font-semibold text-slate-500 dark:text-slate-400">
                {' '}
                – {formatPrice(product.maxPrice)}
              </span>
            )}
          </p>

          <p
            className={`text-2xs mt-0.5 font-semibold ${
              soldOut
                ? 'text-slate-400 dark:text-slate-500'
                : product.availableQuantity <= 5
                  ? 'text-amber-600 dark:text-amber-400'
                  : 'text-emerald-600 dark:text-emerald-400'
            }`}
          >
            {soldOut
              ? 'Tạm hết hàng'
              : product.availableQuantity <= 5
                ? `Chỉ còn ${product.availableQuantity} máy`
                : `Còn ${product.availableQuantity} máy`}
          </p>
        </div>
      </div>
    </div>
  )
}
