'use client'

import { useCallback, useEffect, useState } from 'react'
import { Loader2, Package, RefreshCw } from 'lucide-react'
import * as adminApi from '@/lib/api/admin'
import * as orderApi from '@/lib/api/order'
import StatusBadge, { ORDER_STATUS_LABEL } from '@/components/StatusBadge'
import Pagination from '@/components/Pagination'
import Modal from '@/components/Modal'
import { EmptyState, ErrorBox, Loading } from '@/components/Feedback'
import { ApiError } from '@/lib/api/client'
import { useToast } from '@/contexts/ToastContext'
import { formatDateTime, formatPrice } from '@/lib/format'
import type {
  AdminOrderRow,
  OrderResponse,
  OrderStatus,
  PageResponse,
} from '@/types'

const STATUSES: OrderStatus[] = [
  'PENDING_PAYMENT',
  'PAID',
  'PACKED',
  'SHIPPED',
  'DELIVERED',
  'CANCELLED',
  'REFUNDED',
]

export default function AdminOrdersView() {
  const toast = useToast()

  const [data, setData] = useState<PageResponse<AdminOrderRow> | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<{ message: string; code?: string } | null>(null)

  const [status, setStatus] = useState<OrderStatus | ''>('')
  const [from, setFrom] = useState('')
  const [to, setTo] = useState('')
  const [page, setPage] = useState(0)

  const [detail, setDetail] = useState<OrderResponse | null>(null)
  const [detailCode, setDetailCode] = useState<string | null>(null)
  const [nextStatus, setNextStatus] = useState<OrderStatus | ''>('')
  const [note, setNote] = useState('')
  const [saving, setSaving] = useState(false)

  const load = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      setData(
        await adminApi.listOrders({
          status: status || undefined,
          from: from || undefined,
          to: to || undefined,
          page,
          size: 20,
        }),
      )
    } catch (err) {
      setError(
        err instanceof ApiError
          ? { message: err.message, code: err.code }
          : { message: 'Không tải được danh sách đơn hàng' },
      )
    } finally {
      setLoading(false)
    }
  }, [status, from, to, page])

  useEffect(() => {
    void load()
  }, [load])

  const openDetail = async (code: string) => {
    setDetailCode(code)
    setDetail(null)
    setNextStatus('')
    setNote('')
    try {
      setDetail(await orderApi.getOrder(code))
    } catch (err) {
      toast.showError(err, 'Không tải được chi tiết đơn')
      setDetailCode(null)
    }
  }

  const applyStatus = async () => {
    if (!detail || !nextStatus) return
    setSaving(true)
    try {
      const updated = await adminApi.updateOrderStatus(detail.code, nextStatus, note.trim() || undefined)
      setDetail(updated)
      setNextStatus('')
      setNote('')
      toast.success(`Đã chuyển ${detail.code} sang ${ORDER_STATUS_LABEL[updated.status]}`)
      await load()
    } catch (err) {
      // 409 INVALID_STATE_TRANSITION nghĩa là máy trạng thái ở backend không cho —
      // danh sách bên dưới đã lọc sẵn, nên tới đây thường là do đơn vừa đổi ở nơi khác.
      toast.showError(err, 'Không đổi được trạng thái')
    } finally {
      setSaving(false)
    }
  }

  const allowedNext = detail ? adminApi.NEXT_STATUSES[detail.status] : []

  return (
    <div className="flex flex-col gap-4">
      <div className="flex items-end justify-between gap-3 flex-wrap">
        <div>
          <h1 className="page-title">Đơn hàng</h1>
          <p className="page-desc">Lọc theo trạng thái và khoảng ngày đặt</p>
        </div>
        <button onClick={() => void load()} className="btn btn-secondary btn-sm">
          <RefreshCw size={13} /> Làm mới
        </button>
      </div>

      <div className="card-padded grid gap-3 sm:grid-cols-3">
        <div>
          <label className="form-label" htmlFor="ao-status">
            Trạng thái
          </label>
          <select
            id="ao-status"
            value={status}
            onChange={(e) => {
              setStatus(e.target.value as OrderStatus | '')
              setPage(0)
            }}
            className="form-select"
          >
            <option value="">Tất cả</option>
            {STATUSES.map((s) => (
              <option key={s} value={s}>
                {ORDER_STATUS_LABEL[s]}
              </option>
            ))}
          </select>
        </div>

        <div>
          <label className="form-label" htmlFor="ao-from">
            Từ ngày
          </label>
          <input
            id="ao-from"
            type="date"
            value={from}
            onChange={(e) => {
              setFrom(e.target.value)
              setPage(0)
            }}
            className="form-input"
          />
        </div>

        <div>
          <label className="form-label" htmlFor="ao-to">
            Đến ngày
          </label>
          <input
            id="ao-to"
            type="date"
            value={to}
            onChange={(e) => {
              setTo(e.target.value)
              setPage(0)
            }}
            className="form-input"
          />
        </div>
      </div>

      {error && <ErrorBox message={error.message} code={error.code} />}

      <div className="card">
        {loading ? (
          <Loading />
        ) : !data || data.content.length === 0 ? (
          <EmptyState icon={Package} title="Không có đơn hàng nào khớp bộ lọc" />
        ) : (
          <>
            <div className="overflow-x-auto">
              <table className="data-table">
                <thead>
                  <tr>
                    <th>Mã đơn</th>
                    <th>Khách hàng</th>
                    <th>Trạng thái</th>
                    <th className="text-right">Số món</th>
                    <th className="text-right">Tổng tiền</th>
                    <th>Đặt lúc</th>
                    <th />
                  </tr>
                </thead>
                <tbody>
                  {data.content.map((row) => (
                    <tr key={row.code}>
                      <td className="font-mono font-semibold text-slate-800 dark:text-slate-100">
                        {row.code}
                      </td>
                      <td className="text-slate-600 dark:text-slate-300">{row.customerEmail}</td>
                      <td>
                        <StatusBadge kind="order" status={row.status} />
                      </td>
                      <td className="text-right tabular-nums">{row.itemCount}</td>
                      <td className="text-right tabular-nums font-semibold">
                        {formatPrice(row.totalAmount)}
                      </td>
                      <td className="text-2xs text-slate-500 dark:text-slate-400 whitespace-nowrap">
                        {formatDateTime(row.placedAt)}
                      </td>
                      <td className="text-right">
                        <button
                          onClick={() => void openDetail(row.code)}
                          className="btn btn-secondary btn-sm"
                        >
                          Xử lý
                        </button>
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

      {/* ── Hộp thoại xử lý đơn ──────────────────────────────────── */}
      <Modal
        open={detailCode !== null}
        title={`Đơn ${detailCode ?? ''}`}
        onClose={() => setDetailCode(null)}
        wide
      >
        {!detail ? (
          <Loading />
        ) : (
          <div className="flex flex-col gap-4">
            <div className="flex items-center gap-2 flex-wrap">
              <StatusBadge kind="order" status={detail.status} />
              <span className="text-2xs text-slate-500 dark:text-slate-400">
                Đặt lúc {formatDateTime(detail.placedAt)}
              </span>
              <span className="price text-sm ml-auto">{formatPrice(detail.totalAmount)}</span>
            </div>

            <div>
              <p className="section-label mb-1.5">Sản phẩm</p>
              <ul className="flex flex-col divide-y divide-slate-100 dark:divide-slate-700 text-sm">
                {detail.items.map((i) => (
                  <li key={i.id} className="py-2 flex justify-between gap-3">
                    <div className="min-w-0">
                      <p className="font-medium text-slate-800 dark:text-slate-100">
                        {i.productName}
                      </p>
                      <p className="text-2xs text-slate-500 dark:text-slate-400">
                        {i.variantName} · <span className="font-mono">{i.sku}</span> × {i.quantity}
                      </p>
                    </div>
                    <span className="tabular-nums shrink-0">{formatPrice(i.lineTotal)}</span>
                  </li>
                ))}
              </ul>
            </div>

            <div>
              <p className="section-label mb-1.5">Giao đến</p>
              <p className="text-sm text-slate-700 dark:text-slate-200">
                {detail.shippingAddress?.receiverName} · {detail.shippingAddress?.phone}
              </p>
              <p className="text-sm text-slate-600 dark:text-slate-300">
                {[
                  detail.shippingAddress?.street,
                  detail.shippingAddress?.ward,
                  detail.shippingAddress?.district,
                  detail.shippingAddress?.province,
                ]
                  .filter(Boolean)
                  .join(', ')}
              </p>
            </div>

            <div className="pt-3 border-t border-slate-100 dark:border-slate-700">
              <p className="section-label mb-2">Chuyển trạng thái</p>

              {allowedNext.length === 0 ? (
                <p className="text-sm text-slate-500 dark:text-slate-400">
                  Đơn đã ở trạng thái kết thúc, không chuyển tiếp được.
                </p>
              ) : (
                <>
                  <div className="flex flex-wrap gap-1.5 mb-3">
                    {allowedNext.map((s) => (
                      <button
                        key={s}
                        onClick={() => setNextStatus(s)}
                        className={`btn btn-sm ${nextStatus === s ? 'btn-primary' : 'btn-secondary'}`}
                      >
                        {ORDER_STATUS_LABEL[s]}
                      </button>
                    ))}
                  </div>

                  <label className="form-label" htmlFor="ao-note">
                    Ghi chú
                  </label>
                  <textarea
                    id="ao-note"
                    value={note}
                    onChange={(e) => setNote(e.target.value)}
                    maxLength={500}
                    rows={2}
                    placeholder="Lý do, mã vận đơn…"
                    className="form-input resize-none mb-3"
                  />

                  <button
                    onClick={() => void applyStatus()}
                    disabled={!nextStatus || saving}
                    className="btn btn-primary"
                  >
                    {saving ? <Loader2 size={15} className="animate-spin" /> : 'Xác nhận chuyển'}
                  </button>

                  <p className="form-hint">
                    Chuyển sang huỷ hoặc hoàn tiền sẽ tự động hoàn tồn kho và hoàn lượt mã giảm giá.
                  </p>
                </>
              )}
            </div>
          </div>
        )}
      </Modal>
    </div>
  )
}
