'use client'

import { useCallback, useEffect, useMemo, useState } from 'react'
import Link from 'next/link'
import { useRouter, useSearchParams } from 'next/navigation'
import {
  GitCompareArrows,
  PackageSearch,
  SlidersHorizontal,
  X,
} from 'lucide-react'
import * as catalogApi from '@/lib/api/catalog'
import ProductCard from '@/components/ProductCard'
import Pagination from '@/components/Pagination'
import { EmptyState, ErrorBox, Skeleton } from '@/components/Feedback'
import { ApiError } from '@/lib/api/client'
import { specLabel } from '@/lib/format'
import type {
  BrandResponse,
  CategoryResponse,
  PageResponse,
  ProductSummaryResponse,
} from '@/types'
import { ALLOWED_SPEC_KEYS } from '@/types'

/** Backend chỉ cho sắp xếp theo name | price | createdAt. */
const SORT_OPTIONS = [
  { value: 'createdAt,desc', label: 'Mới nhất' },
  { value: 'price,asc', label: 'Giá thấp → cao' },
  { value: 'price,desc', label: 'Giá cao → thấp' },
  { value: 'name,asc', label: 'Tên A → Z' },
] as const

/** Bộ lọc thông số hay dùng nhất — phần còn lại gõ tay ở ô "thông số khác". */
const QUICK_SPEC_KEYS = ['ram', 'storage', 'chipset', 'screen'] as const

const PAGE_SIZE = 20
const MAX_COMPARE = 3

export default function ProductListView() {
  const router = useRouter()
  const searchParams = useSearchParams()

  const [data, setData] = useState<PageResponse<ProductSummaryResponse> | null>(null)
  const [categories, setCategories] = useState<CategoryResponse[]>([])
  const [brands, setBrands] = useState<BrandResponse[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<{ message: string; code?: string } | null>(null)
  const [filtersOpen, setFiltersOpen] = useState(false)
  const [compare, setCompare] = useState<number[]>([])

  // URL là nguồn sự thật của bộ lọc: chia sẻ link ra là người khác thấy đúng
  // kết quả đó, và nút lùi của trình duyệt hoạt động đúng.
  const filters = useMemo(() => {
    const specFilter: Record<string, string> = {}
    for (const key of ALLOWED_SPEC_KEYS) {
      const v = searchParams.get(`spec.${key}`)
      if (v) specFilter[key] = v
    }
    return {
      keyword: searchParams.get('keyword') ?? '',
      categoryId: searchParams.get('categoryId') ?? '',
      brandId: searchParams.get('brandId') ?? '',
      minPrice: searchParams.get('minPrice') ?? '',
      maxPrice: searchParams.get('maxPrice') ?? '',
      inStock: searchParams.get('inStock') === 'true',
      sort: searchParams.get('sort') ?? 'createdAt,desc',
      page: Number(searchParams.get('page') ?? 0),
      specFilter,
    }
  }, [searchParams])

  const activeFilterCount =
    (filters.categoryId ? 1 : 0) +
    (filters.brandId ? 1 : 0) +
    (filters.minPrice ? 1 : 0) +
    (filters.maxPrice ? 1 : 0) +
    (filters.inStock ? 1 : 0) +
    Object.keys(filters.specFilter).length

  /** Ghi bộ lọc ngược vào URL. Đổi bộ lọc thì luôn về trang đầu. */
  const patchQuery = useCallback(
    (patch: Record<string, string | null>, resetPage = true) => {
      const sp = new URLSearchParams(searchParams.toString())
      for (const [k, v] of Object.entries(patch)) {
        if (v === null || v === '') sp.delete(k)
        else sp.set(k, v)
      }
      if (resetPage) sp.delete('page')
      router.push(`/products?${sp.toString()}`, { scroll: false })
    },
    [router, searchParams],
  )

  useEffect(() => {
    Promise.allSettled([catalogApi.listCategories(), catalogApi.listBrands()]).then(
      ([c, b]) => {
        if (c.status === 'fulfilled') setCategories(c.value)
        if (b.status === 'fulfilled') setBrands(b.value)
      },
    )
  }, [])

  useEffect(() => {
    let cancelled = false
    setLoading(true)
    setError(null)

    catalogApi
      .searchProducts({
        keyword: filters.keyword || undefined,
        categoryId: filters.categoryId ? Number(filters.categoryId) : undefined,
        brandId: filters.brandId ? Number(filters.brandId) : undefined,
        minPrice: filters.minPrice ? Number(filters.minPrice) : undefined,
        maxPrice: filters.maxPrice ? Number(filters.maxPrice) : undefined,
        inStock: filters.inStock || undefined,
        specFilter: filters.specFilter,
        sort: filters.sort,
        page: filters.page,
        size: PAGE_SIZE,
      })
      .then((res) => {
        if (!cancelled) setData(res)
      })
      .catch((err) => {
        if (cancelled) return
        setError(
          err instanceof ApiError
            ? { message: err.message, code: err.code }
            : { message: 'Không tải được danh sách sản phẩm' },
        )
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })

    // Bộ lọc đổi nhanh (gõ khoảng giá) sẽ có nhiều lời gọi chồng nhau;
    // cờ này đảm bảo chỉ kết quả của lần gọi mới nhất được hiển thị.
    return () => {
      cancelled = true
    }
  }, [filters])

  const toggleCompare = (id: number) => {
    setCompare((prev) =>
      prev.includes(id)
        ? prev.filter((x) => x !== id)
        : prev.length >= MAX_COMPARE
          ? prev
          : [...prev, id],
    )
  }

  const clearAll = () => {
    const sp = new URLSearchParams()
    if (filters.keyword) sp.set('keyword', filters.keyword)
    router.push(`/products${sp.toString() ? `?${sp}` : ''}`, { scroll: false })
  }

  const filterPanel = (
    <div className="flex flex-col gap-4">
      <div>
        <label className="form-label" htmlFor="f-category">
          Danh mục
        </label>
        <select
          id="f-category"
          value={filters.categoryId}
          onChange={(e) => patchQuery({ categoryId: e.target.value || null })}
          className="form-select"
        >
          <option value="">Tất cả danh mục</option>
          {categories.map((c) => (
            <option key={c.id} value={c.id}>
              {c.parentName ? `${c.parentName} › ${c.name}` : c.name}
            </option>
          ))}
        </select>
      </div>

      <div>
        <label className="form-label" htmlFor="f-brand">
          Thương hiệu
        </label>
        <select
          id="f-brand"
          value={filters.brandId}
          onChange={(e) => patchQuery({ brandId: e.target.value || null })}
          className="form-select"
        >
          <option value="">Tất cả thương hiệu</option>
          {brands.map((b) => (
            <option key={b.id} value={b.id}>
              {b.name}
            </option>
          ))}
        </select>
      </div>

      <div>
        <span className="form-label">Khoảng giá (₫)</span>
        <div className="flex items-center gap-2">
          <input
            type="number"
            min={0}
            step={1000000}
            value={filters.minPrice}
            onChange={(e) => patchQuery({ minPrice: e.target.value || null })}
            placeholder="Từ"
            aria-label="Giá từ"
            className="form-input"
          />
          <span className="text-slate-400 text-2xs">–</span>
          <input
            type="number"
            min={0}
            step={1000000}
            value={filters.maxPrice}
            onChange={(e) => patchQuery({ maxPrice: e.target.value || null })}
            placeholder="Đến"
            aria-label="Giá đến"
            className="form-input"
          />
        </div>
      </div>

      <div>
        <span className="form-label">Thông số kỹ thuật</span>
        <div className="flex flex-col gap-2">
          {QUICK_SPEC_KEYS.map((key) => (
            <input
              key={key}
              value={filters.specFilter[key] ?? ''}
              onChange={(e) => patchQuery({ [`spec.${key}`]: e.target.value || null })}
              placeholder={specLabel(key)}
              aria-label={specLabel(key)}
              className="form-input"
            />
          ))}
        </div>
        <p className="form-hint">Khớp chính xác, ví dụ RAM &ldquo;8GB&rdquo;.</p>
      </div>

      <label className="flex items-center gap-2 cursor-pointer select-none">
        <input
          type="checkbox"
          checked={filters.inStock}
          onChange={(e) => patchQuery({ inStock: e.target.checked ? 'true' : null })}
          className="w-4 h-4 rounded accent-primary-600 cursor-pointer"
        />
        <span className="text-sm text-slate-700 dark:text-slate-200">Chỉ hàng còn bán</span>
      </label>

      {activeFilterCount > 0 && (
        <button onClick={clearAll} className="btn btn-secondary btn-sm w-full">
          <X size={13} /> Xoá {activeFilterCount} bộ lọc
        </button>
      )}
    </div>
  )

  return (
    <div className="flex flex-col gap-4">
      <div className="flex items-end justify-between gap-3 flex-wrap">
        <div>
          <h1 className="page-title">
            {filters.keyword ? `Kết quả cho “${filters.keyword}”` : 'Tất cả sản phẩm'}
          </h1>
          {data && (
            <p className="page-desc">Tìm thấy {data.totalElements} sản phẩm</p>
          )}
        </div>

        <div className="flex items-center gap-2">
          <button
            onClick={() => setFiltersOpen((v) => !v)}
            className="btn btn-secondary btn-sm lg:hidden"
            aria-expanded={filtersOpen}
          >
            <SlidersHorizontal size={14} />
            Lọc
            {activeFilterCount > 0 && (
              <span className="badge badge-indigo">{activeFilterCount}</span>
            )}
          </button>

          <select
            value={filters.sort}
            onChange={(e) => patchQuery({ sort: e.target.value })}
            aria-label="Sắp xếp"
            className="form-select py-2 text-sm w-auto"
          >
            {SORT_OPTIONS.map((o) => (
              <option key={o.value} value={o.value}>
                {o.label}
              </option>
            ))}
          </select>
        </div>
      </div>

      <div className="flex gap-5 items-start">
        <aside className="hidden lg:block w-56 shrink-0 sticky top-20">
          <div className="card-padded">{filterPanel}</div>
        </aside>

        {filtersOpen && (
          <div className="lg:hidden card-padded w-full">{filterPanel}</div>
        )}

        <div className={`flex-1 min-w-0 ${filtersOpen ? 'hidden lg:block' : ''}`}>
          {error && <ErrorBox message={error.message} code={error.code} />}

          {loading ? (
            <div className="grid gap-3 grid-cols-2 md:grid-cols-3 xl:grid-cols-4">
              {Array.from({ length: 8 }).map((_, i) => (
                <Skeleton key={i} className="h-64" />
              ))}
            </div>
          ) : data && data.content.length > 0 ? (
            <>
              <div className="grid gap-3 grid-cols-2 md:grid-cols-3 xl:grid-cols-4">
                {data.content.map((p, i) => (
                  <ProductCard
                    key={p.id}
                    product={p}
                    priority={i < 4}
                    onToggleCompare={toggleCompare}
                    inCompare={compare.includes(p.id)}
                  />
                ))}
              </div>

              <Pagination
                page={data.page}
                totalPages={data.totalPages}
                totalElements={data.totalElements}
                onChange={(p) => {
                  patchQuery({ page: String(p) }, false)
                  window.scrollTo({ top: 0, behavior: 'smooth' })
                }}
              />
            </>
          ) : (
            !error && (
              <div className="card">
                <EmptyState
                  icon={PackageSearch}
                  title="Không tìm thấy sản phẩm nào"
                  desc="Thử nới bộ lọc hoặc dùng từ khoá khác."
                  action={
                    activeFilterCount > 0 ? (
                      <button onClick={clearAll} className="btn btn-secondary">
                        Xoá bộ lọc
                      </button>
                    ) : undefined
                  }
                />
              </div>
            )
          )}
        </div>
      </div>

      {/* Khay so sánh — nổi dưới cùng khi đã chọn sản phẩm */}
      {compare.length > 0 && (
        <div className="fixed bottom-4 left-1/2 -translate-x-1/2 z-40 card px-4 py-3 shadow-lg flex items-center gap-3 animate-slide-up">
          <GitCompareArrows size={16} className="text-primary-600 dark:text-primary-400" />
          <span className="text-sm font-semibold text-slate-700 dark:text-slate-200">
            Đã chọn {compare.length}/{MAX_COMPARE}
          </span>
          <Link
            href={`/compare?ids=${compare.join(',')}`}
            className={`btn btn-primary btn-sm ${compare.length < 2 ? 'pointer-events-none opacity-50' : ''}`}
            aria-disabled={compare.length < 2}
          >
            So sánh
          </Link>
          <button onClick={() => setCompare([])} className="btn-ghost p-1 rounded" aria-label="Bỏ chọn tất cả">
            <X size={15} />
          </button>
        </div>
      )}
    </div>
  )
}
