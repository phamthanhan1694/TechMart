'use client'

import { useEffect, useState } from 'react'
import { CheckCircle2, Loader2, ScanBarcode, Upload } from 'lucide-react'
import * as catalogApi from '@/lib/api/catalog'
import * as warrantyApi from '@/lib/api/warranty'
import { ErrorBox } from '@/components/Feedback'
import { ApiError } from '@/lib/api/client'
import { useToast } from '@/contexts/ToastContext'
import type { BulkSerialResponse, ProductSummaryResponse, VariantResponse } from '@/types'

/**
 * Nhập lô serial/IMEI khi nhận hàng từ nhà cung cấp.
 *
 * Serial đã tồn tại được backend bỏ qua và liệt kê lại, chứ không làm hỏng cả lô —
 * nhập lại một file có vài dòng trùng vẫn chạy được, không phải lọc tay trước.
 */
export default function AdminSerialsView() {
  const toast = useToast()

  const [products, setProducts] = useState<ProductSummaryResponse[]>([])
  const [productSlug, setProductSlug] = useState('')
  const [variants, setVariants] = useState<VariantResponse[]>([])
  const [variantId, setVariantId] = useState('')
  const [warrantyMonths, setWarrantyMonths] = useState('12')
  const [raw, setRaw] = useState('')

  const [submitting, setSubmitting] = useState(false)
  const [result, setResult] = useState<BulkSerialResponse | null>(null)
  const [error, setError] = useState<{ message: string; code?: string } | null>(null)

  useEffect(() => {
    catalogApi
      .searchProducts({ size: 100, sort: 'name,asc' })
      .then((p) => setProducts(p.content))
      .catch(() => setProducts([]))
  }, [])

  useEffect(() => {
    if (!productSlug) {
      setVariants([])
      setVariantId('')
      return
    }
    catalogApi
      .getProduct(productSlug)
      .then((p) => {
        setVariants(p.variants)
        setVariantId(p.variants[0] ? String(p.variants[0].id) : '')
      })
      .catch(() => setVariants([]))
  }, [productSlug])

  // Mỗi dòng một serial; bỏ dòng trống và khoảng trắng thừa khi dán từ Excel.
  const serialNumbers = raw
    .split(/[\n,;]+/)
    .map((s) => s.trim())
    .filter(Boolean)

  const submit = async (e: React.FormEvent) => {
    e.preventDefault()
    setError(null)
    setResult(null)
    setSubmitting(true)
    try {
      const res = await warrantyApi.importSerials(
        Number(variantId),
        serialNumbers,
        warrantyMonths ? Number(warrantyMonths) : undefined,
      )
      setResult(res)
      setRaw('')
      toast.success(`Đã nhập ${res.imported} serial`)
    } catch (err) {
      setError(
        err instanceof ApiError
          ? { message: err.message, code: err.code }
          : { message: 'Không nhập được lô serial' },
      )
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="flex flex-col gap-4 max-w-3xl">
      <div>
        <h1 className="page-title flex items-center gap-2">
          <ScanBarcode size={20} /> Nhập serial / IMEI
        </h1>
        <p className="page-desc">
          Bảo hành được tính từ lúc xuất kho (đơn chuyển sang &ldquo;đã đóng gói&rdquo;), không
          phải từ lúc nhập lô này.
        </p>
      </div>

      {error && <ErrorBox message={error.message} code={error.code} />}

      {result && (
        <div className="card-padded border-l-4 border-l-emerald-500">
          <div className="flex items-start gap-2.5">
            <CheckCircle2 size={18} className="text-emerald-600 dark:text-emerald-400 shrink-0 mt-0.5" />
            <div className="min-w-0">
              <p className="font-bold text-slate-800 dark:text-slate-100">
                Nhập {result.imported} serial thành công
              </p>
              {result.skippedDuplicates > 0 && (
                <>
                  <p className="text-sm text-slate-600 dark:text-slate-300 mt-0.5">
                    Bỏ qua {result.skippedDuplicates} serial đã tồn tại trong hệ thống.
                  </p>
                  <p className="text-2xs font-mono text-slate-500 dark:text-slate-400 mt-1.5 break-all">
                    {result.duplicates.join(', ')}
                  </p>
                </>
              )}
            </div>
          </div>
        </div>
      )}

      <form onSubmit={submit} className="card-padded flex flex-col gap-4">
        <div className="grid gap-3 sm:grid-cols-2">
          <div>
            <label className="form-label" htmlFor="as-product">
              Sản phẩm
            </label>
            <select
              id="as-product"
              value={productSlug}
              onChange={(e) => setProductSlug(e.target.value)}
              required
              className="form-select"
            >
              <option value="">— Chọn sản phẩm —</option>
              {products.map((p) => (
                <option key={p.id} value={p.slug}>
                  {p.name}
                </option>
              ))}
            </select>
          </div>

          <div>
            <label className="form-label" htmlFor="as-variant">
              Phiên bản
            </label>
            <select
              id="as-variant"
              value={variantId}
              onChange={(e) => setVariantId(e.target.value)}
              required
              disabled={variants.length === 0}
              className="form-select"
            >
              {variants.length === 0 && <option value="">— Chọn sản phẩm trước —</option>}
              {variants.map((v) => (
                <option key={v.id} value={v.id}>
                  {v.name} ({v.sku})
                </option>
              ))}
            </select>
          </div>

          <div>
            <label className="form-label" htmlFor="as-months">
              Thời hạn bảo hành (tháng)
            </label>
            <input
              id="as-months"
              type="number"
              min={1}
              max={120}
              value={warrantyMonths}
              onChange={(e) => setWarrantyMonths(e.target.value)}
              className="form-input"
            />
          </div>
        </div>

        <div>
          <label className="form-label" htmlFor="as-serials">
            Danh sách serial
          </label>
          <textarea
            id="as-serials"
            value={raw}
            onChange={(e) => setRaw(e.target.value)}
            rows={10}
            required
            placeholder={'SN-IP15PM-000001\nSN-IP15PM-000002\nSN-IP15PM-000003'}
            className="form-input font-mono resize-y"
          />
          <p className="form-hint">
            Mỗi dòng một serial (dán thẳng từ Excel được). Đã nhận {serialNumbers.length} serial,
            tối đa 1000 mỗi lần.
          </p>
        </div>

        <button
          type="submit"
          disabled={submitting || serialNumbers.length === 0 || !variantId || serialNumbers.length > 1000}
          className="btn btn-primary w-fit"
        >
          {submitting ? (
            <>
              <Loader2 size={15} className="animate-spin" /> Đang nhập…
            </>
          ) : (
            <>
              <Upload size={15} /> Nhập {serialNumbers.length || ''} serial
            </>
          )}
        </button>
      </form>
    </div>
  )
}
