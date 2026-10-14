'use client'

import { useCallback, useEffect, useState } from 'react'
import { Flame, Loader2, Plus, RefreshCw, Scale, Trash2, Zap } from 'lucide-react'
import * as catalogApi from '@/lib/api/catalog'
import * as flashSaleApi from '@/lib/api/flashsale'
import StatusBadge from '@/components/StatusBadge'
import Modal from '@/components/Modal'
import { EmptyState, ErrorBox, Loading } from '@/components/Feedback'
import { ApiError } from '@/lib/api/client'
import { useToast } from '@/contexts/ToastContext'
import { formatDateTime, formatPrice, toDateTimeLocalInput } from '@/lib/format'
import type {
  FlashSaleResponse,
  ProductSummaryResponse,
  VariantResponse,
} from '@/types'

interface DraftItem {
  variantId: string
  variantLabel: string
  originalPrice: number
  salePrice: string
  saleQuantity: string
  perUserLimit: string
}

export default function AdminFlashSaleView() {
  const toast = useToast()

  const [sales, setSales] = useState<FlashSaleResponse[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<{ message: string; code?: string } | null>(null)
  const [busyId, setBusyId] = useState<number | null>(null)

  const [open, setOpen] = useState(false)
  const [saving, setSaving] = useState(false)
  const [name, setName] = useState('')
  const [startsAt, setStartsAt] = useState('')
  const [endsAt, setEndsAt] = useState('')
  const [items, setItems] = useState<DraftItem[]>([])

  const [products, setProducts] = useState<ProductSummaryResponse[]>([])
  const [pickSlug, setPickSlug] = useState('')
  const [pickVariants, setPickVariants] = useState<VariantResponse[]>([])

  const load = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const [active, upcoming] = await Promise.all([
        flashSaleApi.activeFlashSales(),
        flashSaleApi.upcomingFlashSales(),
      ])
      setSales([...active, ...upcoming])
    } catch (err) {
      setError(
        err instanceof ApiError
          ? { message: err.message, code: err.code }
          : { message: 'Không tải được danh sách phiên mở bán' },
      )
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    void load()
  }, [load])

  useEffect(() => {
    if (!open) return
    catalogApi
      .searchProducts({ size: 100, sort: 'name,asc' })
      .then((p) => setProducts(p.content))
      .catch(() => setProducts([]))
  }, [open])

  useEffect(() => {
    if (!pickSlug) {
      setPickVariants([])
      return
    }
    catalogApi
      .getProduct(pickSlug)
      .then((p) => setPickVariants(p.variants))
      .catch(() => setPickVariants([]))
  }, [pickSlug])

  const openCreate = () => {
    const start = new Date()
    start.setMinutes(start.getMinutes() + 10)
    const end = new Date()
    end.setHours(end.getHours() + 2)
    setName('')
    setStartsAt(toDateTimeLocalInput(start))
    setEndsAt(toDateTimeLocalInput(end))
    setItems([])
    setPickSlug('')
    setOpen(true)
  }

  const addItem = (v: VariantResponse) => {
    if (items.some((it) => it.variantId === String(v.id))) return
    setItems((prev) => [
      ...prev,
      {
        variantId: String(v.id),
        variantLabel: `${v.name} (${v.sku})`,
        originalPrice: v.price,
        salePrice: '',
        saleQuantity: '',
        perUserLimit: '1',
      },
    ])
  }

  const patchItem = (index: number, patch: Partial<DraftItem>) => {
    setItems((prev) => prev.map((it, i) => (i === index ? { ...it, ...patch } : it)))
  }

  const create = async (e: React.FormEvent) => {
    e.preventDefault()
    if (items.length === 0) {
      toast.error('Phiên mở bán phải có ít nhất một suất')
      return
    }
    setSaving(true)
    try {
      await flashSaleApi.createFlashSale({
        name: name.trim(),
        startsAt: new Date(startsAt).toISOString(),
        endsAt: new Date(endsAt).toISOString(),
        items: items.map((it) => ({
          variantId: Number(it.variantId),
          salePrice: Number(it.salePrice),
          saleQuantity: Number(it.saleQuantity),
          perUserLimit: it.perUserLimit ? Number(it.perUserLimit) : undefined,
        })),
      })
      setOpen(false)
      toast.success('Đã tạo phiên mở bán')
      await load()
    } catch (err) {
      toast.showError(err, 'Không tạo được phiên mở bán')
    } finally {
      setSaving(false)
    }
  }

  const warmUp = async (sale: FlashSaleResponse) => {
    setBusyId(sale.id)
    try {
      const res = await flashSaleApi.warmUpFlashSale(sale.id)
      toast.success(`Đã nạp ${res.warmedUpItems} suất vào Redis`)
      await load()
    } catch (err) {
      toast.showError(err, 'Không nạp trước được')
    } finally {
      setBusyId(null)
    }
  }

  const reconcile = async (sale: FlashSaleResponse) => {
    setBusyId(sale.id)
    try {
      const res = await flashSaleApi.reconcileFlashSale(sale.id)
      toast.success(res.status)
      await load()
    } catch (err) {
      toast.showError(err, 'Không đối soát được')
    } finally {
      setBusyId(null)
    }
  }

  return (
    <div className="flex flex-col gap-4">
      <div className="flex items-end justify-between gap-3 flex-wrap">
        <div>
          <h1 className="page-title">Flash sale</h1>
          <p className="page-desc">
            Nạp tồn kho vào Redis <span className="font-semibold">trước</span> giờ mở bán. Không nạp
            trước thì đúng giây mở bán sẽ có hàng nghìn request cùng nạp một lúc.
          </p>
        </div>
        <div className="flex gap-2">
          <button onClick={() => void load()} className="btn btn-secondary btn-sm">
            <RefreshCw size={13} /> Làm mới
          </button>
          <button onClick={openCreate} className="btn btn-primary btn-sm">
            <Plus size={14} /> Tạo phiên
          </button>
        </div>
      </div>

      {error && <ErrorBox message={error.message} code={error.code} />}

      {loading ? (
        <Loading />
      ) : sales.length === 0 ? (
        <div className="card">
          <EmptyState
            icon={Zap}
            title="Chưa có phiên mở bán nào"
            desc="Tạo một phiên, nạp trước tồn kho, rồi mở bán."
          />
        </div>
      ) : (
        <div className="flex flex-col gap-3">
          {sales.map((sale) => (
            <section key={sale.id} className="card">
              <header className="flex items-center gap-2.5 px-4 py-3 border-b border-slate-200 dark:border-slate-700 flex-wrap">
                <h2 className="font-bold text-sm text-slate-800 dark:text-slate-100">
                  {sale.name}
                </h2>
                <StatusBadge kind="flashSale" status={sale.status} />
                <span className="text-2xs text-slate-500 dark:text-slate-400">
                  {formatDateTime(sale.startsAt)} → {formatDateTime(sale.endsAt)}
                </span>

                <div className="flex gap-1.5 ml-auto">
                  <button
                    onClick={() => void warmUp(sale)}
                    disabled={busyId === sale.id || sale.status === 'ENDED'}
                    className="btn btn-secondary btn-sm"
                  >
                    {busyId === sale.id ? (
                      <Loader2 size={12} className="animate-spin" />
                    ) : (
                      <Flame size={12} />
                    )}
                    Nạp trước
                  </button>
                  <button
                    onClick={() => void reconcile(sale)}
                    disabled={busyId === sale.id}
                    className="btn btn-secondary btn-sm"
                    title="Đối soát số suất Redis với DB sau khi phiên kết thúc"
                  >
                    <Scale size={12} /> Đối soát
                  </button>
                </div>
              </header>

              <div className="overflow-x-auto">
                <table className="data-table">
                  <thead>
                    <tr>
                      <th>Sản phẩm</th>
                      <th className="text-right">Giá gốc</th>
                      <th className="text-right">Giá bán</th>
                      <th className="text-right">Giảm</th>
                      <th className="text-right">Đã bán</th>
                      <th className="text-right">Còn suất</th>
                    </tr>
                  </thead>
                  <tbody>
                    {sale.items.map((item) => (
                      <tr key={item.id}>
                        <td>
                          <p className="font-semibold text-slate-800 dark:text-slate-100">
                            {item.productName}
                          </p>
                          <p className="text-2xs text-slate-500 dark:text-slate-400">
                            {item.variantName}
                          </p>
                        </td>
                        <td className="text-right tabular-nums text-slate-500 dark:text-slate-400">
                          {formatPrice(item.originalPrice)}
                        </td>
                        <td className="text-right tabular-nums font-semibold">
                          {formatPrice(item.salePrice)}
                        </td>
                        <td className="text-right">
                          <span className="badge badge-red">-{item.discountPercent}%</span>
                        </td>
                        <td className="text-right tabular-nums">
                          {item.saleQuantity - item.remaining} / {item.saleQuantity}
                        </td>
                        <td className="text-right">
                          <span
                            className={`badge ${item.remaining > 0 ? 'badge-emerald' : 'badge-slate'}`}
                          >
                            {item.remaining}
                          </span>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </section>
          ))}
        </div>
      )}

      {/* ── Tạo phiên mở bán ─────────────────────────────────────── */}
      <Modal open={open} title="Tạo phiên mở bán" onClose={() => setOpen(false)} wide>
        <form onSubmit={create} className="flex flex-col gap-4">
          <div className="grid gap-3 sm:grid-cols-3">
            <div className="sm:col-span-3">
              <label className="form-label" htmlFor="fs-name">
                Tên phiên
              </label>
              <input
                id="fs-name"
                value={name}
                onChange={(e) => setName(e.target.value)}
                required
                maxLength={150}
                placeholder="Săn deal cuối tuần"
                className="form-input"
              />
            </div>

            <div>
              <label className="form-label" htmlFor="fs-start">
                Bắt đầu
              </label>
              <input
                id="fs-start"
                type="datetime-local"
                value={startsAt}
                onChange={(e) => setStartsAt(e.target.value)}
                required
                className="form-input"
              />
            </div>

            <div>
              <label className="form-label" htmlFor="fs-end">
                Kết thúc
              </label>
              <input
                id="fs-end"
                type="datetime-local"
                value={endsAt}
                onChange={(e) => setEndsAt(e.target.value)}
                required
                className="form-input"
              />
            </div>
          </div>

          <div className="pt-3 border-t border-slate-100 dark:border-slate-700">
            <p className="section-label mb-2">Thêm suất</p>
            <div className="grid gap-2 sm:grid-cols-2">
              <select
                value={pickSlug}
                onChange={(e) => setPickSlug(e.target.value)}
                aria-label="Chọn sản phẩm"
                className="form-select"
              >
                <option value="">— Chọn sản phẩm —</option>
                {products.map((p) => (
                  <option key={p.id} value={p.slug}>
                    {p.name}
                  </option>
                ))}
              </select>

              <select
                value=""
                onChange={(e) => {
                  const v = pickVariants.find((x) => String(x.id) === e.target.value)
                  if (v) addItem(v)
                }}
                disabled={pickVariants.length === 0}
                aria-label="Chọn phiên bản để thêm"
                className="form-select"
              >
                <option value="">
                  {pickVariants.length === 0 ? '— Chọn sản phẩm trước —' : '— Thêm phiên bản —'}
                </option>
                {pickVariants.map((v) => (
                  <option key={v.id} value={v.id}>
                    {v.name} ({v.sku}) · còn {v.availableQuantity}
                  </option>
                ))}
              </select>
            </div>
          </div>

          {items.length > 0 && (
            <div className="overflow-x-auto">
              <table className="data-table">
                <thead>
                  <tr>
                    <th>Phiên bản</th>
                    <th className="w-32">Giá bán (₫)</th>
                    <th className="w-24">Số suất</th>
                    <th className="w-24">Mỗi khách</th>
                    <th />
                  </tr>
                </thead>
                <tbody>
                  {items.map((it, i) => (
                    <tr key={it.variantId}>
                      <td>
                        <p className="font-medium text-slate-800 dark:text-slate-100">
                          {it.variantLabel}
                        </p>
                        <p className="text-2xs text-slate-500 dark:text-slate-400">
                          Giá gốc {formatPrice(it.originalPrice)}
                        </p>
                      </td>
                      <td>
                        <input
                          type="number"
                          min={1}
                          step={1000}
                          value={it.salePrice}
                          onChange={(e) => patchItem(i, { salePrice: e.target.value })}
                          required
                          aria-label="Giá bán"
                          className="form-input py-1.5 text-2xs"
                        />
                      </td>
                      <td>
                        <input
                          type="number"
                          min={1}
                          value={it.saleQuantity}
                          onChange={(e) => patchItem(i, { saleQuantity: e.target.value })}
                          required
                          aria-label="Số suất"
                          className="form-input py-1.5 text-2xs"
                        />
                      </td>
                      <td>
                        <input
                          type="number"
                          min={1}
                          value={it.perUserLimit}
                          onChange={(e) => patchItem(i, { perUserLimit: e.target.value })}
                          aria-label="Giới hạn mỗi khách"
                          className="form-input py-1.5 text-2xs"
                        />
                      </td>
                      <td className="text-right">
                        <button
                          type="button"
                          onClick={() => setItems((prev) => prev.filter((_, x) => x !== i))}
                          className="btn btn-danger btn-sm"
                          aria-label="Bỏ suất này"
                        >
                          <Trash2 size={12} />
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}

          <div className="flex justify-end gap-2 pt-2 border-t border-slate-100 dark:border-slate-700">
            <button type="button" onClick={() => setOpen(false)} className="btn btn-secondary">
              Huỷ
            </button>
            <button type="submit" disabled={saving || items.length === 0} className="btn btn-primary">
              {saving ? <Loader2 size={15} className="animate-spin" /> : 'Tạo phiên'}
            </button>
          </div>
        </form>
      </Modal>
    </div>
  )
}
