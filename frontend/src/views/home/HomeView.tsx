'use client'

import { useEffect, useState } from 'react'
import Link from 'next/link'
import { ArrowRight, BadgeCheck, Clock, PackageSearch, ShieldCheck, Truck, Zap } from 'lucide-react'
import * as catalogApi from '@/lib/api/catalog'
import * as flashSaleApi from '@/lib/api/flashsale'
import ProductCard from '@/components/ProductCard'
import ProductImage from '@/components/ProductImage'
import Countdown from '@/components/Countdown'
import { EmptyState, Skeleton } from '@/components/Feedback'
import { formatPrice } from '@/lib/format'
import type {
  CategoryResponse,
  FlashSaleResponse,
  ProductSummaryResponse,
} from '@/types'

export default function HomeView() {
  const [products, setProducts] = useState<ProductSummaryResponse[]>([])
  const [categories, setCategories] = useState<CategoryResponse[]>([])
  const [flashSale, setFlashSale] = useState<FlashSaleResponse | null>(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    // Ba lời gọi độc lập nhau — chạy song song, và một cái hỏng không được
    // kéo theo cả trang trắng.
    Promise.allSettled([
      catalogApi.searchProducts({ size: 10, sort: 'createdAt,desc' }),
      catalogApi.listCategories(),
      flashSaleApi.activeFlashSales(),
    ])
      .then(([p, c, f]) => {
        if (p.status === 'fulfilled') setProducts(p.value.content)
        if (c.status === 'fulfilled') setCategories(c.value)
        if (f.status === 'fulfilled') setFlashSale(f.value[0] ?? null)
      })
      .finally(() => setLoading(false))
  }, [])

  return (
    <div className="flex flex-col gap-8">
      {/* ── Băng rôn ───────────────────────────────────────────────── */}
      <section className="rounded-2xl bg-gradient-to-br from-primary-700 via-primary-600 to-primary-500 text-white p-6 sm:p-10 overflow-hidden relative">
        <div className="relative z-10 max-w-xl">
          <p className="text-2xs font-bold tracking-[0.12em] uppercase text-primary-100 mb-2">
            Điện thoại · Laptop · Phụ kiện
          </p>
          <h1 className="text-2xl sm:text-3xl font-extrabold leading-tight">
            Điện thoại &amp; laptop chính hãng, giá tốt mỗi ngày
          </h1>
          <p className="text-sm text-primary-100 mt-2.5 leading-relaxed">
            Trả góp 0% · Giao nhanh toàn quốc · Đổi trả trong 7 ngày
          </p>
          <div className="flex flex-wrap gap-2 mt-5">
            <Link
              href="/products"
              className="btn bg-white text-primary-700 border-white hover:bg-primary-50"
            >
              Xem sản phẩm <ArrowRight size={15} />
            </Link>
            <Link
              href="/flash-sales"
              className="btn bg-white/10 text-white border-white/30 hover:bg-white/20"
            >
              <Zap size={15} /> Flash sale
            </Link>
          </div>
        </div>
        <div
          aria-hidden
          className="absolute -right-16 -bottom-16 w-64 h-64 rounded-full bg-white/10 blur-2xl"
        />
      </section>

      {/* ── Ba cam kết ─────────────────────────────────────────────── */}
      <section className="grid gap-3 sm:grid-cols-3">
        {[
          {
            icon: BadgeCheck,
            title: 'Chính hãng 100%',
            desc: 'Máy mới nguyên seal, đầy đủ hoá đơn VAT và phiếu bảo hành.',
          },
          {
            icon: ShieldCheck,
            title: 'Bảo hành 12 tháng',
            desc: 'Bảo hành chính hãng tại hơn 30 trung tâm trên toàn quốc.',
          },
          {
            icon: Truck,
            title: 'Giao nhanh 2 giờ',
            desc: 'Miễn phí giao hàng nội thành. Kiểm tra máy trước khi thanh toán.',
          },
        ].map((f) => (
          <div key={f.title} className="card-surface p-4 flex gap-3">
            <span className="w-9 h-9 shrink-0 rounded-lg bg-primary-50 dark:bg-primary-900/30 text-primary-600 dark:text-primary-400 flex items-center justify-center">
              <f.icon size={17} />
            </span>
            <div>
              <p className="font-bold text-sm text-slate-800 dark:text-slate-100">{f.title}</p>
              <p className="text-2xs text-slate-500 dark:text-slate-400 leading-relaxed mt-0.5">
                {f.desc}
              </p>
            </div>
          </div>
        ))}
      </section>

      {/* ── Flash sale đang chạy ───────────────────────────────────── */}
      {flashSale && flashSale.items.length > 0 && (
        <section className="card-surface overflow-hidden">
          <header className="flex items-center gap-2.5 px-4 py-3 bg-gradient-to-r from-primary-600 to-primary-500 text-white">
            <Zap size={17} className="shrink-0" />
            <h2 className="font-extrabold text-base">{flashSale.name}</h2>
            <span className="flex items-center gap-1 ml-auto text-2xs font-bold bg-white/20 px-2 py-1 rounded-md">
              <Clock size={12} />
              <Countdown until={flashSale.endsAt} className="text-white" />
            </span>
          </header>

          <div className="p-4 grid gap-3 grid-cols-2 md:grid-cols-4">
            {flashSale.items.slice(0, 4).map((item) => {
              const sold = item.saleQuantity - item.remaining
              const soldPercent = item.saleQuantity > 0 ? (sold / item.saleQuantity) * 100 : 0
              return (
                <Link
                  key={item.id}
                  href="/flash-sales"
                  className="flex flex-col gap-2 group"
                >
                  <div className="relative">
                    <ProductImage src={item.thumbnailUrl} alt={item.productName} />
                    <span className="absolute top-1.5 left-1.5 badge badge-red">
                      -{item.discountPercent}%
                    </span>
                  </div>
                  <p className="text-2xs font-semibold clamp-2 text-slate-700 dark:text-slate-200 group-hover:text-primary-600 dark:group-hover:text-primary-400 transition-colors">
                    {item.productName}
                  </p>
                  <div>
                    <p className="price text-sm">{formatPrice(item.salePrice)}</p>
                    <p className="price-strike">{formatPrice(item.originalPrice)}</p>
                  </div>
                  <div>
                    <div className="h-1.5 rounded-full bg-slate-200 dark:bg-slate-700 overflow-hidden">
                      <div
                        className="h-full bg-gradient-to-r from-primary-600 to-primary-500"
                        style={{ width: `${Math.min(100, soldPercent)}%` }}
                      />
                    </div>
                    <p className="text-2xs text-slate-500 dark:text-slate-400 mt-1">
                      {item.remaining > 0 ? `Còn ${item.remaining} sản phẩm` : 'Đã bán hết'}
                    </p>
                  </div>
                </Link>
              )
            })}
          </div>
        </section>
      )}

      {/* ── Danh mục ───────────────────────────────────────────────── */}
      {categories.length > 0 && (
        <section>
          <h2 className="page-title mb-3">Danh mục</h2>
          <div className="flex flex-wrap gap-2">
            {categories.map((c) => (
              <Link
                key={c.id}
                href={`/products?categoryId=${c.id}`}
                className="px-3.5 py-2 rounded-lg text-sm font-semibold bg-white border border-slate-200 text-slate-700 hover:border-primary-300 hover:text-primary-700 dark:bg-surface-card dark:border-surface-border dark:text-slate-200 dark:hover:border-primary-500 dark:hover:text-primary-400 transition-colors"
              >
                {c.name}
              </Link>
            ))}
          </div>
        </section>
      )}

      {/* ── Sản phẩm mới ───────────────────────────────────────────── */}
      <section>
        <div className="flex items-center justify-between gap-3 mb-3">
          <h2 className="page-title">Sản phẩm mới</h2>
          <Link
            href="/products"
            className="text-sm font-semibold text-primary-600 dark:text-primary-400 hover:underline underline-offset-2 flex items-center gap-1"
          >
            Xem tất cả <ArrowRight size={14} />
          </Link>
        </div>

        {loading ? (
          <div className="grid gap-3 grid-cols-2 md:grid-cols-4 lg:grid-cols-5">
            {Array.from({ length: 10 }).map((_, i) => (
              <Skeleton key={i} className="h-64" />
            ))}
          </div>
        ) : products.length === 0 ? (
          <div className="card">
            <EmptyState
              icon={PackageSearch}
              title="Chưa có sản phẩm nào"
              desc="Cửa hàng đang cập nhật hàng mới, bạn quay lại sau nhé."
            />
          </div>
        ) : (
          <div className="grid gap-3 grid-cols-2 md:grid-cols-4 lg:grid-cols-5">
            {products.map((p, i) => (
              <ProductCard key={p.id} product={p} priority={i < 5} />
            ))}
          </div>
        )}
      </section>
    </div>
  )
}
