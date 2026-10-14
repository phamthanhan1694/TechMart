'use client'

import { useCallback, useEffect, useState } from 'react'
import Link from 'next/link'
import {
  ArrowLeft,
  CreditCard,
  Loader2,
  MapPin,
  PackageX,
  Star,
  XCircle,
} from 'lucide-react'
import * as orderApi from '@/lib/api/order'
import * as paymentApi from '@/lib/api/payment'
import * as reviewApi from '@/lib/api/review'
import StatusBadge from '@/components/StatusBadge'
import StarRating from '@/components/StarRating'
import Modal from '@/components/Modal'
import { EmptyState, Loading } from '@/components/Feedback'
import { ApiError } from '@/lib/api/client'
import { useToast } from '@/contexts/ToastContext'
import { formatDateTime, formatPrice } from '@/lib/format'
import type {
  OrderItemResponse,
  OrderResponse,
  PaymentMethod,
  PaymentResponse,
  StatusHistoryResponse,
} from '@/types'

export default function OrderDetailView({ code }: { code: string }) {
  const toast = useToast()

  const [order, setOrder] = useState<OrderResponse | null>(null)
  const [history, setHistory] = useState<StatusHistoryResponse[]>([])
  const [payments, setPayments] = useState<PaymentResponse[]>([])
  const [loading, setLoading] = useState(true)
  const [notFound, setNotFound] = useState(false)

  const [paying, setPaying] = useState(false)
  const [cancelOpen, setCancelOpen] = useState(false)
  const [cancelReason, setCancelReason] = useState('')
  const [cancelling, setCancelling] = useState(false)

  const [reviewItem, setReviewItem] = useState<OrderItemResponse | null>(null)
  const [rating, setRating] = useState(5)
  const [comment, setComment] = useState('')
  const [submittingReview, setSubmittingReview] = useState(false)
  const [reviewedItemIds, setReviewedItemIds] = useState<number[]>([])

  const load = useCallback(async () => {
    setLoading(true)
    try {
      const o = await orderApi.getOrder(code)
      setOrder(o)
      // Lịch sử và giao dịch chỉ là thông tin phụ — hỏng thì trang vẫn dùng được.
      const [h, p] = await Promise.allSettled([
        orderApi.getOrderHistory(code),
        paymentApi.paymentsOfOrder(code),
      ])
      if (h.status === 'fulfilled') setHistory(h.value)
      if (p.status === 'fulfilled') setPayments(p.value)
    } catch {
      // Đơn của người khác cũng trả 404 — cố ý, để không lộ mã đơn nào có thật.
      setNotFound(true)
    } finally {
      setLoading(false)
    }
  }, [code])

  useEffect(() => {
    void load()
  }, [load])

  const handlePay = async (method: PaymentMethod) => {
    setPaying(true)
    try {
      // Cổng thật sẽ chuyển hướng người dùng rồi gọi webhook. Ở đây backend có sẵn
      // endpoint ký HMAC nên chạy trọn vòng khởi tạo → ký → webhook ngay tại chỗ.
      const result = await paymentApi.payNow(code, method)
      if (result.status === 'SUCCEEDED') toast.success('Thanh toán thành công')
      else toast.error(result.failureReason || 'Thanh toán không thành công')
      await load()
    } catch (err) {
      toast.showError(err, 'Không thực hiện được thanh toán')
    } finally {
      setPaying(false)
    }
  }

  const handleCancel = async () => {
    setCancelling(true)
    try {
      const updated = await orderApi.cancelOrder(code, cancelReason.trim() || undefined)
      setOrder(updated)
      setCancelOpen(false)
      setCancelReason('')
      toast.success('Đã huỷ đơn hàng')
      await load()
    } catch (err) {
      toast.showError(err, 'Không huỷ được đơn hàng')
    } finally {
      setCancelling(false)
    }
  }

  const submitReview = async () => {
    if (!reviewItem) return
    setSubmittingReview(true)
    try {
      await reviewApi.createReview({
        orderItemId: reviewItem.id,
        rating,
        comment: comment.trim() || undefined,
      })
      setReviewedItemIds((prev) => [...prev, reviewItem.id])
      setReviewItem(null)
      setRating(5)
      setComment('')
      toast.success('Cảm ơn bạn đã đánh giá')
    } catch (err) {
      if (err instanceof ApiError && err.code === 'REVIEW_ALREADY_EXISTS') {
        setReviewedItemIds((prev) => [...prev, reviewItem.id])
        setReviewItem(null)
      }
      toast.showError(err, 'Không gửi được đánh giá')
    } finally {
      setSubmittingReview(false)
    }
  }

  if (loading) return <Loading label="Đang tải đơn hàng…" />

  if (notFound || !order) {
    return (
      <div className="card">
        <EmptyState
          icon={PackageX}
          title="Không tìm thấy đơn hàng"
          desc={`Không có đơn nào mang mã ${code} thuộc về tài khoản của bạn.`}
          action={
            <Link href="/orders" className="btn btn-primary">
              Về danh sách đơn
            </Link>
          }
        />
      </div>
    )
  }

  const canPay = order.status === 'PENDING_PAYMENT'
  const canCancel = orderApi.isCancellable(order.status)
  const canReview = order.status === 'DELIVERED'
  const addr = order.shippingAddress ?? {}

  return (
    <div className="flex flex-col gap-4">
      <Link
        href="/orders"
        className="inline-flex items-center gap-1.5 text-sm font-semibold text-slate-500 hover:text-primary-600 dark:text-slate-400 dark:hover:text-primary-400 transition-colors w-fit"
      >
        <ArrowLeft size={15} /> Đơn hàng của tôi
      </Link>

      <div className="flex items-start justify-between gap-3 flex-wrap">
        <div>
          <div className="flex items-center gap-2 flex-wrap">
            <h1 className="page-title font-mono">{order.code}</h1>
            <StatusBadge kind="order" status={order.status} />
          </div>
          <p className="page-desc">Đặt lúc {formatDateTime(order.placedAt)}</p>
        </div>

        <div className="flex items-center gap-2">
          {canCancel && (
            <button onClick={() => setCancelOpen(true)} className="btn btn-secondary btn-sm">
              <XCircle size={14} /> Huỷ đơn
            </button>
          )}
        </div>
      </div>

      <div className="grid gap-4 lg:grid-cols-[minmax(0,1fr)_20rem] items-start">
        <div className="flex flex-col gap-4">
          {/* ── Sản phẩm ─────────────────────────────────────────── */}
          <section className="card-padded">
            <h2 className="text-base font-bold text-slate-800 dark:text-slate-100 mb-3">
              Sản phẩm ({order.items.length})
            </h2>
            <ul className="flex flex-col divide-y divide-slate-100 dark:divide-slate-700">
              {order.items.map((item) => {
                const reviewed = reviewedItemIds.includes(item.id)
                return (
                  <li key={item.id} className="py-3 first:pt-0 flex gap-3 items-start">
                    <div className="flex-1 min-w-0">
                      <p className="text-sm font-semibold text-slate-800 dark:text-slate-100">
                        {item.productName}
                      </p>
                      <p className="text-2xs text-slate-500 dark:text-slate-400">
                        {item.variantName} · <span className="font-mono">{item.sku}</span>
                      </p>
                      <p className="text-2xs text-slate-500 dark:text-slate-400 mt-0.5">
                        {formatPrice(item.unitPrice)} × {item.quantity}
                      </p>

                      {canReview && (
                        <button
                          onClick={() => setReviewItem(item)}
                          disabled={reviewed}
                          className="btn btn-secondary btn-sm mt-2"
                        >
                          <Star size={12} />
                          {reviewed ? 'Đã đánh giá' : 'Viết đánh giá'}
                        </button>
                      )}
                    </div>

                    <p className="text-sm font-bold text-slate-800 dark:text-slate-100 tabular-nums shrink-0">
                      {formatPrice(item.lineTotal)}
                    </p>
                  </li>
                )
              })}
            </ul>
          </section>

          {/* ── Địa chỉ ──────────────────────────────────────────── */}
          <section className="card-padded">
            <h2 className="text-base font-bold text-slate-800 dark:text-slate-100 mb-2 flex items-center gap-2">
              <MapPin size={16} className="text-slate-400" /> Địa chỉ giao hàng
            </h2>
            <p className="text-sm font-semibold text-slate-800 dark:text-slate-100">
              {addr.receiverName} · {addr.phone}
            </p>
            <p className="text-sm text-slate-600 dark:text-slate-300 mt-0.5">
              {[addr.street, addr.ward, addr.district, addr.province]
                .filter(Boolean)
                .join(', ')}
            </p>
            {order.note && (
              <p className="text-2xs text-slate-500 dark:text-slate-400 mt-2 italic">
                Ghi chú: {order.note}
              </p>
            )}
          </section>

          {/* ── Lịch sử trạng thái ───────────────────────────────── */}
          {history.length > 0 && (
            <section className="card-padded">
              <h2 className="text-base font-bold text-slate-800 dark:text-slate-100 mb-3">
                Lịch sử trạng thái
              </h2>
              <ol className="flex flex-col gap-3">
                {history.map((h, i) => (
                  <li key={i} className="flex gap-3">
                    <div className="flex flex-col items-center shrink-0">
                      <span className="w-2 h-2 rounded-full bg-primary-500 mt-1.5" />
                      {i < history.length - 1 && (
                        <span className="w-px flex-1 bg-slate-200 dark:bg-slate-700 mt-1" />
                      )}
                    </div>
                    <div className="pb-1">
                      <div className="flex items-center gap-2 flex-wrap">
                        <StatusBadge kind="order" status={h.toStatus} />
                        <span className="text-2xs text-slate-500 dark:text-slate-400">
                          {formatDateTime(h.createdAt)}
                        </span>
                      </div>
                      {h.note && (
                        <p className="text-2xs text-slate-500 dark:text-slate-400 mt-1">{h.note}</p>
                      )}
                    </div>
                  </li>
                ))}
              </ol>
            </section>
          )}

          {/* ── Giao dịch thanh toán ─────────────────────────────── */}
          {payments.length > 0 && (
            <section className="card-padded">
              <h2 className="text-base font-bold text-slate-800 dark:text-slate-100 mb-3">
                Giao dịch thanh toán
              </h2>
              <div className="overflow-x-auto">
                <table className="data-table">
                  <thead>
                    <tr>
                      <th>Mã giao dịch</th>
                      <th>Phương thức</th>
                      <th>Số tiền</th>
                      <th>Trạng thái</th>
                      <th>Thời điểm</th>
                    </tr>
                  </thead>
                  <tbody>
                    {payments.map((p) => (
                      <tr key={p.id}>
                        <td className="font-mono text-2xs">{p.transactionRef}</td>
                        <td>{p.method === 'COD' ? 'Khi nhận hàng' : 'Thẻ'}</td>
                        <td className="tabular-nums">{formatPrice(p.amount)}</td>
                        <td>
                          <StatusBadge kind="payment" status={p.status} />
                        </td>
                        <td className="text-2xs text-slate-500 dark:text-slate-400">
                          {formatDateTime(p.paidAt)}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </section>
          )}
        </div>

        {/* ── Cột tổng tiền / thanh toán ──────────────────────────── */}
        <aside className="card-padded lg:sticky lg:top-20 flex flex-col gap-3">
          <h2 className="text-base font-bold text-slate-800 dark:text-slate-100">
            Tổng thanh toán
          </h2>

          <dl className="flex flex-col gap-1.5 text-sm">
            <div className="flex justify-between">
              <dt className="text-slate-500 dark:text-slate-400">Tạm tính</dt>
              <dd className="tabular-nums text-slate-800 dark:text-slate-100">
                {formatPrice(order.subtotal)}
              </dd>
            </div>
            {order.discountAmount > 0 && (
              <div className="flex justify-between">
                <dt className="text-slate-500 dark:text-slate-400">
                  Giảm giá {order.couponCode && `(${order.couponCode})`}
                </dt>
                <dd className="tabular-nums text-emerald-600 dark:text-emerald-400">
                  −{formatPrice(order.discountAmount)}
                </dd>
              </div>
            )}
            <div className="flex justify-between">
              <dt className="text-slate-500 dark:text-slate-400">Phí vận chuyển</dt>
              <dd className="tabular-nums text-slate-800 dark:text-slate-100">
                {order.shippingFee > 0 ? formatPrice(order.shippingFee) : 'Miễn phí'}
              </dd>
            </div>
          </dl>

          <div className="flex justify-between items-baseline pt-3 border-t border-slate-100 dark:border-slate-700">
            <span className="font-bold text-slate-800 dark:text-slate-100">Tổng cộng</span>
            <span className="price text-lg">{formatPrice(order.totalAmount)}</span>
          </div>

          {canPay && (
            <div className="flex flex-col gap-2 pt-2">
              <button
                onClick={() => void handlePay('MOCK_CARD')}
                disabled={paying}
                className="btn btn-buy w-full"
              >
                {paying ? (
                  <>
                    <Loader2 size={15} className="animate-spin" /> Đang xử lý…
                  </>
                ) : (
                  <>
                    <CreditCard size={15} /> Thanh toán bằng thẻ
                  </>
                )}
              </button>
              <button
                onClick={() => void handlePay('COD')}
                disabled={paying}
                className="btn btn-secondary w-full"
              >
                Thanh toán khi nhận hàng
              </button>
              <p className="form-hint">
                Thanh toán được bảo mật. Shop không lưu thông tin thẻ của bạn.
              </p>
            </div>
          )}
        </aside>
      </div>

      {/* ── Hộp thoại huỷ đơn ────────────────────────────────────── */}
      <Modal
        open={cancelOpen}
        title="Huỷ đơn hàng"
        onClose={() => setCancelOpen(false)}
        footer={
          <>
            <button onClick={() => setCancelOpen(false)} className="btn btn-secondary">
              Giữ đơn
            </button>
            <button
              onClick={() => void handleCancel()}
              disabled={cancelling}
              className="btn btn-primary bg-red-600 border-red-600 hover:bg-red-700 hover:border-red-700"
            >
              {cancelling ? <Loader2 size={15} className="animate-spin" /> : 'Xác nhận huỷ'}
            </button>
          </>
        }
      >
        <p className="text-sm text-slate-600 dark:text-slate-300 mb-3 leading-relaxed">
          Bạn chắc chắn muốn huỷ đơn <span className="font-mono font-bold">{order.code}</span>?
          Mã giảm giá đã dùng (nếu có) sẽ được hoàn lại cho bạn.
        </p>
        <label className="form-label" htmlFor="cancel-reason">
          Lý do huỷ
        </label>
        <textarea
          id="cancel-reason"
          value={cancelReason}
          onChange={(e) => setCancelReason(e.target.value)}
          maxLength={500}
          rows={3}
          placeholder="Không bắt buộc"
          className="form-input resize-none"
        />
      </Modal>

      {/* ── Hộp thoại viết đánh giá ──────────────────────────────── */}
      <Modal
        open={reviewItem !== null}
        title="Viết đánh giá"
        onClose={() => setReviewItem(null)}
        footer={
          <>
            <button onClick={() => setReviewItem(null)} className="btn btn-secondary">
              Huỷ
            </button>
            <button
              onClick={() => void submitReview()}
              disabled={submittingReview}
              className="btn btn-primary"
            >
              {submittingReview ? (
                <Loader2 size={15} className="animate-spin" />
              ) : (
                'Gửi đánh giá'
              )}
            </button>
          </>
        }
      >
        {reviewItem && (
          <>
            <p className="text-sm font-semibold text-slate-800 dark:text-slate-100">
              {reviewItem.productName}
            </p>
            <p className="text-2xs text-slate-500 dark:text-slate-400 mb-4">
              {reviewItem.variantName}
            </p>

            <span className="form-label">Số sao</span>
            <StarRating value={rating} size={24} onChange={setRating} className="mb-4" />

            <label className="form-label" htmlFor="review-comment">
              Nhận xét
            </label>
            <textarea
              id="review-comment"
              value={comment}
              onChange={(e) => setComment(e.target.value)}
              maxLength={2000}
              rows={4}
              placeholder="Máy dùng có mượt không, pin trụ được bao lâu…"
              className="form-input resize-none"
            />
            <p className="form-hint">Mỗi sản phẩm đã mua chỉ đánh giá được một lần.</p>
          </>
        )}
      </Modal>
    </div>
  )
}
