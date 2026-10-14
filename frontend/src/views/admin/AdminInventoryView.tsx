'use client'

import { useCallback, useEffect, useState } from 'react'
import { PackageCheck, RefreshCw } from 'lucide-react'
import * as adminApi from '@/lib/api/admin'
import { EmptyState, ErrorBox, Loading } from '@/components/Feedback'
import { ApiError } from '@/lib/api/client'
import { formatNumber } from '@/lib/format'
import type { LowStockItem } from '@/types'

export default function AdminInventoryView() {
  const [items, setItems] = useState<LowStockItem[]>([])
  const [threshold, setThreshold] = useState(10)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<{ message: string; code?: string } | null>(null)

  const load = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      setItems(await adminApi.lowStock(threshold))
    } catch (err) {
      setError(
        err instanceof ApiError
          ? { message: err.message, code: err.code }
          : { message: 'Không tải được danh sách tồn kho' },
      )
    } finally {
      setLoading(false)
    }
  }, [threshold])

  useEffect(() => {
    void load()
  }, [load])

  return (
    <div className="flex flex-col gap-4">
      <div className="flex items-end justify-between gap-3 flex-wrap">
        <div>
          <h1 className="page-title">Tồn kho sắp hết</h1>
          <p className="page-desc">
            <span className="font-semibold">Còn bán</span> = tồn kho − đang giữ. Phần đang giữ là
            hàng khách đã vào bước thanh toán nhưng chưa đặt đơn.
          </p>
        </div>
        <button onClick={() => void load()} className="btn btn-secondary btn-sm">
          <RefreshCw size={13} /> Làm mới
        </button>
      </div>

      <div className="card-padded max-w-xs">
        <label className="form-label" htmlFor="ai-threshold">
          Ngưỡng cảnh báo
        </label>
        <input
          id="ai-threshold"
          type="number"
          min={1}
          max={1000}
          value={threshold}
          onChange={(e) => setThreshold(Math.max(1, Number(e.target.value) || 1))}
          className="form-input"
        />
        <p className="form-hint">Hiện các phiên bản có số hàng còn bán dưới ngưỡng này.</p>
      </div>

      {error && <ErrorBox message={error.message} code={error.code} />}

      <div className="card">
        {loading ? (
          <Loading />
        ) : items.length === 0 ? (
          <EmptyState
            icon={PackageCheck}
            title="Không có phiên bản nào sắp hết"
            desc={`Mọi phiên bản đều còn trên ${threshold} sản phẩm.`}
          />
        ) : (
          <div className="overflow-x-auto">
            <table className="data-table">
              <thead>
                <tr>
                  <th>Phiên bản</th>
                  <th className="text-right">Tồn kho</th>
                  <th className="text-right">Đang giữ</th>
                  <th className="text-right">Còn bán</th>
                </tr>
              </thead>
              <tbody>
                {items.map((it) => (
                  <tr key={it.variantId}>
                    <td>
                      <p className="font-semibold text-slate-800 dark:text-slate-100">
                        {it.productName}
                      </p>
                      <p className="text-2xs text-slate-500 dark:text-slate-400">
                        {it.variantName} · <span className="font-mono">{it.sku}</span>
                      </p>
                    </td>
                    <td className="text-right tabular-nums">{formatNumber(it.stockQuantity)}</td>
                    <td className="text-right tabular-nums text-slate-500 dark:text-slate-400">
                      {formatNumber(it.reservedQuantity)}
                    </td>
                    <td className="text-right">
                      <span
                        className={`badge ${
                          it.availableQuantity <= 0
                            ? 'badge-red'
                            : it.availableQuantity <= 5
                              ? 'badge-amber'
                              : 'badge-slate'
                        }`}
                      >
                        {it.availableQuantity}
                      </span>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  )
}
