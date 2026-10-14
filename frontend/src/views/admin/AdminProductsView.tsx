'use client'

import { useCallback, useEffect, useState } from 'react'
import Link from 'next/link'
import { Boxes, ExternalLink, Loader2, Plus, Search, Trash2 } from 'lucide-react'
import * as catalogApi from '@/lib/api/catalog'
import Pagination from '@/components/Pagination'
import Modal from '@/components/Modal'
import ProductImage from '@/components/ProductImage'
import { EmptyState, ErrorBox, Loading } from '@/components/Feedback'
import { ApiError } from '@/lib/api/client'
import { useToast } from '@/contexts/ToastContext'
import { formatPrice } from '@/lib/format'
import type {
  BrandResponse,
  CategoryResponse,
  PageResponse,
  ProductDetailResponse,
  ProductSummaryResponse,
} from '@/types'

/** Trạng thái biểu mẫu tạo sản phẩm — sản phẩm bắt buộc có ít nhất một phiên bản. */
const BLANK_FORM = {
  name: '',
  slug: '',
  categoryId: '',
  brandId: '',
  description: '',
  thumbnailUrl: '',
  variantSku: '',
  variantName: '',
  variantPrice: '',
  variantStock: '',
}

const COMBINING_MARKS = new RegExp('[\u0300-\u036f]', 'g')

export default function AdminProductsView() {
  const toast = useToast()

  const [data, setData] = useState<PageResponse<ProductSummaryResponse> | null>(null)
  const [categories, setCategories] = useState<CategoryResponse[]>([])
  const [brands, setBrands] = useState<BrandResponse[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<{ message: string; code?: string } | null>(null)

  const [keyword, setKeyword] = useState('')
  const [page, setPage] = useState(0)

  const [createOpen, setCreateOpen] = useState(false)
  const [form, setForm] = useState(BLANK_FORM)
  const [saving, setSaving] = useState(false)

  const [stockTarget, setStockTarget] = useState<ProductDetailResponse | null>(null)
  const [loadingDetail, setLoadingDetail] = useState(false)
  const [adjustments, setAdjustments] = useState<Record<number, string>>({})
  const [adjustReason, setAdjustReason] = useState('')
  const [adjusting, setAdjusting] = useState<number | null>(null)

  const load = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      setData(
        await catalogApi.searchProducts({
          keyword: keyword.trim() || undefined,
          page,
          size: 20,
          sort: 'createdAt,desc',
        }),
      )
    } catch (err) {
      setError(
        err instanceof ApiError
          ? { message: err.message, code: err.code }
          : { message: 'Không tải được danh sách sản phẩm' },
      )
    } finally {
      setLoading(false)
    }
  }, [keyword, page])

  useEffect(() => {
    void load()
  }, [load])

  useEffect(() => {
    Promise.allSettled([catalogApi.listCategories(), catalogApi.listBrands()]).then(
      ([c, b]) => {
        if (c.status === 'fulfilled') setCategories(c.value)
        if (b.status === 'fulfilled') setBrands(b.value)
      },
    )
  }, [])

  /** "iPhone 15 Pro Max 256GB" → "iphone-15-pro-max-256gb"; backend chỉ nhận [a-z0-9-]. */
  const slugify = (s: string) =>
    s
      // NFD tách chữ và dấu thành hai ký tự, rồi bỏ hẳn dải dấu thanh/dấu mũ.
      .normalize('NFD')
      .replace(COMBINING_MARKS, '') // bỏ dấu tiếng Việt sau khi tách tổ hợp
      .replace(/[đĐ]/g, 'd')
      .toLowerCase()
      .replace(/[^a-z0-9]+/g, '-')
      .replace(/^-+|-+$/g, '')

  const createProduct = async (e: React.FormEvent) => {
    e.preventDefault()
    setSaving(true)
    try {
      await catalogApi.createProduct({
        name: form.name.trim(),
        slug: form.slug.trim() || slugify(form.name),
        categoryId: Number(form.categoryId),
        brandId: Number(form.brandId),
        description: form.description.trim() || undefined,
        thumbnailUrl: form.thumbnailUrl.trim() || undefined,
        variants: [
          {
            sku: form.variantSku.trim(),
            name: form.variantName.trim(),
            price: Number(form.variantPrice),
            stockQuantity: Number(form.variantStock),
          },
        ],
      })
      setCreateOpen(false)
      setForm(BLANK_FORM)
      toast.success('Đã tạo sản phẩm')
      await load()
    } catch (err) {
      toast.showError(err, 'Không tạo được sản phẩm')
    } finally {
      setSaving(false)
    }
  }

  const openStock = async (slug: string) => {
    setLoadingDetail(true)
    setAdjustments({})
    setAdjustReason('')
    try {
      setStockTarget(await catalogApi.getProduct(slug))
    } catch (err) {
      toast.showError(err, 'Không tải được sản phẩm')
    } finally {
      setLoadingDetail(false)
    }
  }

  const applyAdjust = async (variantId: number) => {
    const raw = adjustments[variantId]
    const delta = Number(raw)
    if (!raw || Number.isNaN(delta) || delta === 0) return
    if (!adjustReason.trim()) {
      toast.error('Cần ghi lý do điều chỉnh')
      return
    }

    setAdjusting(variantId)
    try {
      await catalogApi.adjustStock(variantId, delta, adjustReason.trim())
      toast.success(`Đã ${delta > 0 ? 'tăng' : 'giảm'} ${Math.abs(delta)} sản phẩm`)
      // Nạp lại để thấy availableQuantity mới thay vì tự cộng trừ ở client.
      if (stockTarget) setStockTarget(await catalogApi.getProduct(stockTarget.slug))
      setAdjustments((prev) => ({ ...prev, [variantId]: '' }))
      await load()
    } catch (err) {
      toast.showError(err, 'Không điều chỉnh được tồn kho')
    } finally {
      setAdjusting(null)
    }
  }

  const removeProduct = async (id: number, name: string) => {
    if (!window.confirm(`Gỡ "${name}" khỏi cửa hàng? Sản phẩm chỉ bị xoá mềm, đơn cũ vẫn giữ nguyên.`)) {
      return
    }
    try {
      await catalogApi.deleteProduct(id)
      toast.success('Đã gỡ sản phẩm')
      await load()
    } catch (err) {
      toast.showError(err, 'Không gỡ được sản phẩm')
    }
  }

  return (
    <div className="flex flex-col gap-4">
      <div className="flex items-end justify-between gap-3 flex-wrap">
        <div>
          <h1 className="page-title">Sản phẩm</h1>
          <p className="page-desc">Tạo sản phẩm, xem phiên bản và điều chỉnh tồn kho</p>
        </div>
        <button onClick={() => setCreateOpen(true)} className="btn btn-primary btn-sm">
          <Plus size={14} /> Thêm sản phẩm
        </button>
      </div>

      <div className="card-padded">
        <label className="form-label" htmlFor="ap-search">
          Tìm kiếm
        </label>
        <div className="relative">
          <Search
            size={15}
            className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400 pointer-events-none"
          />
          <input
            id="ap-search"
            value={keyword}
            onChange={(e) => {
              setKeyword(e.target.value)
              setPage(0)
            }}
            placeholder="Tên sản phẩm…"
            className="form-input pl-9"
          />
        </div>
      </div>

      {error && <ErrorBox message={error.message} code={error.code} />}

      <div className="card">
        {loading ? (
          <Loading />
        ) : !data || data.content.length === 0 ? (
          <EmptyState icon={Boxes} title="Chưa có sản phẩm nào" />
        ) : (
          <>
            <div className="overflow-x-auto">
              <table className="data-table">
                <thead>
                  <tr>
                    <th>Sản phẩm</th>
                    <th>Danh mục</th>
                    <th className="text-right">Giá</th>
                    <th className="text-right">Còn bán</th>
                    <th />
                  </tr>
                </thead>
                <tbody>
                  {data.content.map((p) => (
                    <tr key={p.id}>
                      <td>
                        <div className="flex items-center gap-2.5">
                          <div className="w-10 shrink-0">
                            <ProductImage src={p.thumbnailUrl} alt={p.name} sizes="40px" />
                          </div>
                          <div className="min-w-0">
                            <p className="font-semibold text-slate-800 dark:text-slate-100">
                              {p.name}
                            </p>
                            <p className="text-2xs text-slate-500 dark:text-slate-400">
                              {p.brandName}
                            </p>
                          </div>
                        </div>
                      </td>
                      <td className="text-slate-600 dark:text-slate-300">{p.categoryName}</td>
                      <td className="text-right tabular-nums whitespace-nowrap">
                        {formatPrice(p.minPrice)}
                      </td>
                      <td className="text-right">
                        <span
                          className={`badge ${
                            p.availableQuantity <= 0
                              ? 'badge-slate'
                              : p.availableQuantity <= 10
                                ? 'badge-amber'
                                : 'badge-emerald'
                          }`}
                        >
                          {p.availableQuantity}
                        </span>
                      </td>
                      <td>
                        <div className="flex items-center justify-end gap-1">
                          <button
                            onClick={() => void openStock(p.slug)}
                            className="btn btn-secondary btn-sm"
                          >
                            Tồn kho
                          </button>
                          <Link
                            href={`/products/${p.slug}`}
                            target="_blank"
                            className="btn btn-ghost btn-sm"
                            aria-label="Xem trang sản phẩm"
                          >
                            <ExternalLink size={13} />
                          </Link>
                          <button
                            onClick={() => void removeProduct(p.id, p.name)}
                            className="btn btn-danger btn-sm"
                            aria-label={`Gỡ ${p.name}`}
                          >
                            <Trash2 size={13} />
                          </button>
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>

            <div className="px-3">
              <Pagination
                page={data.page}
                totalPages={data.totalPages}
                totalElements={data.totalElements}
                onChange={setPage}
              />
            </div>
          </>
        )}
      </div>

      {/* ── Tạo sản phẩm ─────────────────────────────────────────── */}
      <Modal open={createOpen} title="Thêm sản phẩm" onClose={() => setCreateOpen(false)} wide>
        <form onSubmit={createProduct} className="flex flex-col gap-3">
          <div className="grid gap-3 sm:grid-cols-2">
            <div className="sm:col-span-2">
              <label className="form-label" htmlFor="np-name">
                Tên sản phẩm
              </label>
              <input
                id="np-name"
                value={form.name}
                onChange={(e) =>
                  setForm({ ...form, name: e.target.value, slug: slugify(e.target.value) })
                }
                required
                maxLength={255}
                className="form-input"
              />
            </div>

            <div className="sm:col-span-2">
              <label className="form-label" htmlFor="np-slug">
                Slug
              </label>
              <input
                id="np-slug"
                value={form.slug}
                onChange={(e) => setForm({ ...form, slug: e.target.value })}
                required
                pattern="[a-z0-9-]+"
                title="Chỉ chữ thường, số và dấu gạch ngang"
                className="form-input font-mono"
              />
              <p className="form-hint">Tự sinh từ tên, sửa được. Chỉ chữ thường, số, gạch ngang.</p>
            </div>

            <div>
              <label className="form-label" htmlFor="np-category">
                Danh mục
              </label>
              <select
                id="np-category"
                value={form.categoryId}
                onChange={(e) => setForm({ ...form, categoryId: e.target.value })}
                required
                className="form-select"
              >
                <option value="">— Chọn —</option>
                {categories.map((c) => (
                  <option key={c.id} value={c.id}>
                    {c.name}
                  </option>
                ))}
              </select>
            </div>

            <div>
              <label className="form-label" htmlFor="np-brand">
                Thương hiệu
              </label>
              <select
                id="np-brand"
                value={form.brandId}
                onChange={(e) => setForm({ ...form, brandId: e.target.value })}
                required
                className="form-select"
              >
                <option value="">— Chọn —</option>
                {brands.map((b) => (
                  <option key={b.id} value={b.id}>
                    {b.name}
                  </option>
                ))}
              </select>
            </div>

            <div className="sm:col-span-2">
              <label className="form-label" htmlFor="np-thumb">
                Ảnh đại diện (URL)
              </label>
              <input
                id="np-thumb"
                type="url"
                value={form.thumbnailUrl}
                onChange={(e) => setForm({ ...form, thumbnailUrl: e.target.value })}
                maxLength={500}
                placeholder="https://…"
                className="form-input"
              />
            </div>

            <div className="sm:col-span-2">
              <label className="form-label" htmlFor="np-desc">
                Mô tả
              </label>
              <textarea
                id="np-desc"
                value={form.description}
                onChange={(e) => setForm({ ...form, description: e.target.value })}
                rows={3}
                maxLength={20000}
                className="form-input resize-none"
              />
            </div>
          </div>

          <div className="pt-3 border-t border-slate-100 dark:border-slate-700">
            <p className="section-label mb-2">Phiên bản đầu tiên (bắt buộc)</p>
            <div className="grid gap-3 sm:grid-cols-2">
              <div>
                <label className="form-label" htmlFor="np-sku">
                  SKU
                </label>
                <input
                  id="np-sku"
                  value={form.variantSku}
                  onChange={(e) => setForm({ ...form, variantSku: e.target.value })}
                  required
                  maxLength={64}
                  placeholder="IP15PM-256-BLK"
                  className="form-input font-mono"
                />
              </div>
              <div>
                <label className="form-label" htmlFor="np-vname">
                  Tên phiên bản
                </label>
                <input
                  id="np-vname"
                  value={form.variantName}
                  onChange={(e) => setForm({ ...form, variantName: e.target.value })}
                  required
                  maxLength={150}
                  placeholder="256GB - Titan đen"
                  className="form-input"
                />
              </div>
              <div>
                <label className="form-label" htmlFor="np-price">
                  Giá bán (₫)
                </label>
                <input
                  id="np-price"
                  type="number"
                  min={1}
                  step={1000}
                  value={form.variantPrice}
                  onChange={(e) => setForm({ ...form, variantPrice: e.target.value })}
                  required
                  className="form-input"
                />
              </div>
              <div>
                <label className="form-label" htmlFor="np-stock">
                  Tồn kho ban đầu
                </label>
                <input
                  id="np-stock"
                  type="number"
                  min={0}
                  value={form.variantStock}
                  onChange={(e) => setForm({ ...form, variantStock: e.target.value })}
                  required
                  className="form-input"
                />
              </div>
            </div>
          </div>

          <div className="flex justify-end gap-2 pt-2">
            <button type="button" onClick={() => setCreateOpen(false)} className="btn btn-secondary">
              Huỷ
            </button>
            <button type="submit" disabled={saving} className="btn btn-primary">
              {saving ? <Loader2 size={15} className="animate-spin" /> : 'Tạo sản phẩm'}
            </button>
          </div>
        </form>
      </Modal>

      {/* ── Điều chỉnh tồn kho ───────────────────────────────────── */}
      <Modal
        open={stockTarget !== null || loadingDetail}
        title={stockTarget ? `Tồn kho — ${stockTarget.name}` : 'Đang tải…'}
        onClose={() => setStockTarget(null)}
        wide
      >
        {loadingDetail || !stockTarget ? (
          <Loading />
        ) : (
          <div className="flex flex-col gap-4">
            <div>
              <label className="form-label" htmlFor="adj-reason">
                Lý do điều chỉnh
              </label>
              <input
                id="adj-reason"
                value={adjustReason}
                onChange={(e) => setAdjustReason(e.target.value)}
                maxLength={255}
                placeholder="Kiểm kê định kỳ, nhập thêm từ NCC, hàng lỗi…"
                className="form-input"
              />
              <p className="form-hint">Bắt buộc — mỗi lần điều chỉnh đều được ghi log kèm lý do.</p>
            </div>

            <div className="overflow-x-auto">
              <table className="data-table">
                <thead>
                  <tr>
                    <th>Phiên bản</th>
                    <th className="text-right">Còn bán</th>
                    <th className="w-40">Điều chỉnh</th>
                  </tr>
                </thead>
                <tbody>
                  {stockTarget.variants.map((v) => (
                    <tr key={v.id}>
                      <td>
                        <p className="font-medium text-slate-800 dark:text-slate-100">{v.name}</p>
                        <p className="text-2xs font-mono text-slate-500 dark:text-slate-400">
                          {v.sku}
                        </p>
                      </td>
                      <td className="text-right tabular-nums font-semibold">
                        {v.availableQuantity}
                      </td>
                      <td>
                        <div className="flex gap-1.5">
                          <input
                            type="number"
                            value={adjustments[v.id] ?? ''}
                            onChange={(e) =>
                              setAdjustments((prev) => ({ ...prev, [v.id]: e.target.value }))
                            }
                            placeholder="±0"
                            aria-label={`Điều chỉnh ${v.sku}`}
                            className="form-input py-1.5 text-2xs w-20"
                          />
                          <button
                            onClick={() => void applyAdjust(v.id)}
                            disabled={adjusting === v.id || !adjustments[v.id]}
                            className="btn btn-primary btn-sm"
                          >
                            {adjusting === v.id ? (
                              <Loader2 size={12} className="animate-spin" />
                            ) : (
                              'Áp dụng'
                            )}
                          </button>
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>

            <p className="form-hint">
              Số âm là giảm, số dương là tăng. &ldquo;Còn bán&rdquo; đã trừ phần đang giữ cho khách
              trong lúc thanh toán.
            </p>
          </div>
        )}
      </Modal>
    </div>
  )
}
