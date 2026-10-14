'use client'

import { useEffect, useMemo, useState } from 'react'
import Link from 'next/link'
import { useRouter } from 'next/navigation'
import {
  BadgeCheck,
  ChevronRight,
  Loader2,
  PackageX,
  RotateCcw,
  ShieldCheck,
  ShoppingCart,
  Truck,
} from 'lucide-react'
import * as catalogApi from '@/lib/api/catalog'
import ProductImage from '@/components/ProductImage'
import QuantityStepper from '@/components/QuantityStepper'
import ReviewSection from '@/views/catalog/ReviewSection'
import WishlistButton from '@/components/WishlistButton'
import { EmptyState, Loading } from '@/components/Feedback'
import { useAuth } from '@/contexts/AuthContext'
import { useCart } from '@/contexts/CartContext'
import { useToast } from '@/contexts/ToastContext'
import { formatPrice, specLabel, specValue } from '@/lib/format'
import type { ProductDetailResponse, VariantResponse } from '@/types'

export default function ProductDetailView({ slug }: { slug: string }) {
  const router = useRouter()
  const { user } = useAuth()
  const { addItem } = useCart()
  const toast = useToast()

  const [product, setProduct] = useState<ProductDetailResponse | null>(null)
  const [loading, setLoading] = useState(true)
  const [notFound, setNotFound] = useState(false)
  const [variantId, setVariantId] = useState<number | null>(null)
  const [quantity, setQuantity] = useState(1)
  const [activeImage, setActiveImage] = useState(0)
  const [adding, setAdding] = useState(false)

  useEffect(() => {
    setLoading(true)
    setNotFound(false)
    catalogApi
      .getProduct(slug)
      .then((p) => {
        setProduct(p)
        // Mặc định chọn phiên bản còn hàng đầu tiên — chọn sẵn một phiên bản
        // hết hàng thì khách phải tự mò mới bấm mua được.
        const firstAvailable = p.variants.find((v) => v.availableQuantity > 0)
        setVariantId((firstAvailable ?? p.variants[0])?.id ?? null)
      })
      .catch(() => setNotFound(true))
      .finally(() => setLoading(false))
  }, [slug])

  const variant: VariantResponse | null = useMemo(
    () => product?.variants.find((v) => v.id === variantId) ?? null,
    [product, variantId],
  )

  // Đổi phiên bản thì số lượng phải co lại theo tồn kho của phiên bản mới.
  useEffect(() => {
    if (variant) setQuantity((q) => Math.max(1, Math.min(q, Math.max(1, variant.availableQuantity))))
  }, [variant])

  const gallery = useMemo(() => {
    if (!product) return []
    const all = [product.thumbnailUrl, ...product.images].filter(
      (x): x is string => Boolean(x),
    )
    return Array.from(new Set(all))
  }, [product])

  const handleAddToCart = async () => {
    if (!variant) return
    if (!user) {
      router.push(`/login?next=${encodeURIComponent(`/products/${slug}`)}`)
      return
    }
    if (user.role !== 'CUSTOMER') {
      toast.info('Chỉ tài khoản khách hàng mới có giỏ hàng')
      return
    }
    setAdding(true)
    try {
      await addItem(variant.id, quantity)
      toast.success(`Đã thêm ${quantity} × ${product?.name} vào giỏ`)
    } catch (err) {
      toast.showError(err, 'Không thêm được vào giỏ hàng')
    } finally {
      setAdding(false)
    }
  }

  if (loading) return <Loading label="Đang tải sản phẩm…" />

  if (notFound || !product) {
    return (
      <div className="card">
        <EmptyState
          icon={PackageX}
          title="Không tìm thấy sản phẩm"
          desc="Sản phẩm có thể đã bị gỡ khỏi cửa hàng."
          action={
            <Link href="/products" className="btn btn-primary">
              Xem sản phẩm khác
            </Link>
          }
        />
      </div>
    )
  }

  const spec = product.spec ?? {}
  const specEntries = Object.entries(spec)
  const soldOut = !variant || variant.availableQuantity <= 0

  return (
    <div className="flex flex-col gap-5">
      {/* Đường dẫn phân cấp */}
      <nav className="flex items-center gap-1 text-2xs text-slate-500 dark:text-slate-400 flex-wrap">
        <Link href="/products" className="hover:text-primary-600 dark:hover:text-primary-400">
          Sản phẩm
        </Link>
        <ChevronRight size={12} />
        <Link
          href={`/products?categoryId=${product.category.id}`}
          className="hover:text-primary-600 dark:hover:text-primary-400"
        >
          {product.category.name}
        </Link>
        <ChevronRight size={12} />
        <span className="text-slate-700 dark:text-slate-300 font-medium truncate">
          {product.name}
        </span>
      </nav>

      <div className="grid gap-5 lg:grid-cols-[minmax(0,1fr)_22rem] items-start">
        {/* ── Ảnh + mô tả + thông số ─────────────────────────────── */}
        <div className="flex flex-col gap-5">
          <div className="card-padded">
            <div className="grid gap-4 sm:grid-cols-[minmax(0,1fr)_minmax(0,1fr)]">
              <div className="flex flex-col gap-2">
                <ProductImage
                  src={gallery[activeImage] ?? product.thumbnailUrl}
                  alt={product.name}
                  sizes="(max-width: 640px) 90vw, 320px"
                  priority
                />
                {gallery.length > 1 && (
                  <div className="flex gap-2 overflow-x-auto pb-1">
                    {gallery.map((url, i) => (
                      <button
                        key={url}
                        onClick={() => setActiveImage(i)}
                        aria-label={`Ảnh ${i + 1}`}
                        aria-current={i === activeImage}
                        className={`w-14 shrink-0 rounded-lg border-2 transition-colors ${
                          i === activeImage
                            ? 'border-primary-500'
                            : 'border-transparent hover:border-slate-300 dark:hover:border-slate-600'
                        }`}
                      >
                        <ProductImage src={url} alt="" sizes="56px" />
                      </button>
                    ))}
                  </div>
                )}
              </div>

              <div className="flex flex-col gap-2">
                <Link
                  href={`/products?brandId=${product.brand.id}`}
                  className="text-2xs font-bold uppercase tracking-wider text-primary-600 dark:text-primary-400 hover:underline underline-offset-2"
                >
                  {product.brand.name}
                </Link>
                <h1 className="text-lg font-extrabold text-slate-900 dark:text-slate-50 leading-snug">
                  {product.name}
                </h1>

                {variant && (
                  <div className="mt-1">
                    <p className="price text-2xl">{formatPrice(variant.price)}</p>
                    {variant.compareAtPrice && variant.compareAtPrice > variant.price && (
                      <p className="price-strike text-sm">{formatPrice(variant.compareAtPrice)}</p>
                    )}
                    <p className="text-2xs text-slate-500 dark:text-slate-400 mt-0.5 font-mono">
                      SKU {variant.sku}
                    </p>
                  </div>
                )}

                {specEntries.length > 0 && (
                  <ul className="mt-2 flex flex-col gap-1">
                    {specEntries.slice(0, 5).map(([k, v]) => (
                      <li key={k} className="flex gap-2 text-2xs">
                        <span className="text-slate-500 dark:text-slate-400 w-24 shrink-0">
                          {specLabel(k)}
                        </span>
                        <span className="text-slate-700 dark:text-slate-200 font-medium">
                          {specValue(v)}
                        </span>
                      </li>
                    ))}
                  </ul>
                )}
              </div>
            </div>
          </div>

          {product.description && (
            <section className="card-padded">
              <h2 className="text-base font-bold text-slate-800 dark:text-slate-100 mb-2">
                Mô tả sản phẩm
              </h2>
              <p className="text-sm text-slate-600 dark:text-slate-300 leading-relaxed whitespace-pre-line">
                {product.description}
              </p>
            </section>
          )}

          {specEntries.length > 0 && (
            <section className="card-padded">
              <h2 className="text-base font-bold text-slate-800 dark:text-slate-100 mb-3">
                Thông số kỹ thuật
              </h2>
              <div className="overflow-x-auto">
                <table className="data-table">
                  <tbody>
                    {specEntries.map(([k, v]) => (
                      <tr key={k}>
                        <td className="text-slate-500 dark:text-slate-400 w-44">{specLabel(k)}</td>
                        <td className="font-medium text-slate-800 dark:text-slate-100">
                          {specValue(v)}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </section>
          )}

          <ReviewSection productId={product.id} />
        </div>

        {/* ── Cột mua hàng ───────────────────────────────────────── */}
        <aside className="card-padded lg:sticky lg:top-20 flex flex-col gap-4">
          <div>
            <span className="form-label">Phiên bản</span>
            <div className="flex flex-col gap-1.5">
              {product.variants.map((v) => {
                const out = v.availableQuantity <= 0
                const selected = v.id === variantId
                return (
                  <button
                    key={v.id}
                    onClick={() => setVariantId(v.id)}
                    disabled={out}
                    className={`text-left px-3 py-2.5 rounded-lg border transition-all ${
                      selected
                        ? 'border-primary-500 bg-primary-50 dark:bg-primary-900/25'
                        : 'border-slate-200 hover:border-slate-300 dark:border-surface-border dark:hover:border-slate-500'
                    } ${out ? 'opacity-50 cursor-not-allowed' : 'cursor-pointer'}`}
                  >
                    <div className="flex items-center justify-between gap-2">
                      <span className="text-sm font-semibold text-slate-800 dark:text-slate-100">
                        {v.name}
                      </span>
                      <span className="text-sm font-bold text-primary-600 dark:text-primary-400 tabular-nums shrink-0">
                        {formatPrice(v.price)}
                      </span>
                    </div>
                    <p
                      className={`text-2xs mt-0.5 ${
                        out
                          ? 'text-slate-400 dark:text-slate-500'
                          : v.availableQuantity <= 5
                            ? 'text-amber-600 dark:text-amber-400'
                            : 'text-emerald-600 dark:text-emerald-400'
                      }`}
                    >
                      {out ? 'Hết hàng' : `Còn ${v.availableQuantity} máy`}
                    </p>
                  </button>
                )
              })}
            </div>
          </div>

          {!soldOut && (
            <div>
              <span className="form-label">Số lượng</span>
              <QuantityStepper
                value={quantity}
                onChange={setQuantity}
                max={variant?.availableQuantity}
              />
              <p className="form-hint">
                Mua trên 10 máy? Gọi <span className="font-semibold">1800 2097</span> để được báo giá
                sỉ.
              </p>
            </div>
          )}

          <button
            onClick={handleAddToCart}
            disabled={soldOut || adding}
            className="btn btn-buy w-full py-3"
          >
            {adding ? (
              <>
                <Loader2 size={16} className="animate-spin" /> Đang thêm…
              </>
            ) : soldOut ? (
              'Tạm hết hàng'
            ) : (
              <>
                <ShoppingCart size={16} /> Thêm vào giỏ
              </>
            )}
          </button>

          <WishlistButton productId={product.id} productName={product.name} variant="full" />

          <ul className="flex flex-col gap-2 pt-3 border-t border-slate-100 dark:border-slate-700">
            <li className="flex items-center gap-2 text-2xs text-slate-600 dark:text-slate-300">
              <BadgeCheck size={14} className="text-emerald-600 dark:text-emerald-400 shrink-0" />
              Máy mới 100%, nguyên seal, đầy đủ hoá đơn VAT
            </li>
            <li className="flex items-center gap-2 text-2xs text-slate-600 dark:text-slate-300">
              <ShieldCheck size={14} className="text-emerald-600 dark:text-emerald-400 shrink-0" />
              Bảo hành chính hãng 12 tháng
            </li>
            <li className="flex items-center gap-2 text-2xs text-slate-600 dark:text-slate-300">
              <Truck size={14} className="text-primary-600 dark:text-primary-400 shrink-0" />
              Giao nhanh 2 giờ nội thành · kiểm tra máy trước khi nhận
            </li>
            <li className="flex items-center gap-2 text-2xs text-slate-600 dark:text-slate-300">
              <RotateCcw size={14} className="text-primary-600 dark:text-primary-400 shrink-0" />
              Đổi trả miễn phí trong 7 ngày
            </li>
          </ul>
        </aside>
      </div>
    </div>
  )
}
