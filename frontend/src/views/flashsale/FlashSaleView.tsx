'use client'

import { useCallback, useEffect, useRef, useState } from 'react'
import { useRouter } from 'next/navigation'
import { Clock, Loader2, Zap } from 'lucide-react'
import * as flashSaleApi from '@/lib/api/flashsale'
import ProductImage from '@/components/ProductImage'
import Countdown from '@/components/Countdown'
import StatusBadge from '@/components/StatusBadge'
import QuantityStepper from '@/components/QuantityStepper'
import Modal from '@/components/Modal'
import { EmptyState, Loading } from '@/components/Feedback'
import { ApiError, newIdempotencyKey } from '@/lib/api/client'
import { useAuth } from '@/contexts/AuthContext'
import { useToast } from '@/contexts/ToastContext'
import { formatDateTime, formatPrice } from '@/lib/format'
import type {
  FlashSaleItemResponse,
  FlashSaleResponse,
  ShippingAddress,
} from '@/types'

const EMPTY_ADDRESS: ShippingAddress = {
  receiverName: '',
  phone: '',
  street: '',
  ward: '',
  district: '',
  province: '',
}

/** Làm mới số suất còn lại. Đủ nhanh để thấy suất giảm, đủ chậm để không tự DDoS. */
const REFRESH_MS = 10_000

export default function FlashSaleView() {
  const router = useRouter()
  const { user } = useAuth()
  const toast = useToast()

  const [active, setActive] = useState<FlashSaleResponse[]>([])
  const [upcoming, setUpcoming] = useState<FlashSaleResponse[]>([])
  const [loading, setLoading] = useState(true)

  const [buying, setBuying] = useState<FlashSaleItemResponse | null>(null)
  const [quantity, setQuantity] = useState(1)
  const [address, setAddress] = useState<ShippingAddress>(EMPTY_ADDRESS)
  const [submitting, setSubmitting] = useState(false)

  // Một key cho mỗi lượt mở hộp thoại mua: bấm lại vì mạng chậm không tạo đơn thứ hai.
  const idempotencyKey = useRef(newIdempotencyKey())

  const load = useCallback(async () => {
    const [a, u] = await Promise.allSettled([
      flashSaleApi.activeFlashSales(),
      flashSaleApi.upcomingFlashSales(),
    ])
    if (a.status === 'fulfilled') setActive(a.value)
    if (u.status === 'fulfilled') setUpcoming(u.value)
    setLoading(false)
  }, [])

  useEffect(() => {
    void load()
    const id = setInterval(() => void load(), REFRESH_MS)
    return () => clearInterval(id)
  }, [load])

  useEffect(() => {
    if (!user) return
    setAddress((a) => ({
      ...a,
      receiverName: a.receiverName || user.fullName || '',
      phone: a.phone || (user.phone && /^0\d{9}$/.test(user.phone) ? user.phone : ''),
    }))
  }, [user])

  const openBuy = (item: FlashSaleItemResponse) => {
    if (!user) {
      router.push(`/login?next=${encodeURIComponent('/flash-sales')}`)
      return
    }
    if (user.role !== 'CUSTOMER') {
      toast.info('Bạn cần đăng nhập bằng tài khoản khách hàng để mua')
      return
    }
    idempotencyKey.current = newIdempotencyKey()
    setQuantity(1)
    setBuying(item)
  }

  const submitPurchase = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!buying) return

    setSubmitting(true)
    try {
      const order = await flashSaleApi.purchaseFlashSaleItem(
        buying.id,
        { quantity, shippingAddress: address },
        idempotencyKey.current,
      )
      setBuying(null)
      toast.success(`Đặt hàng thành công — mã đơn ${order.code}`)
      router.push(`/orders/${order.code}`)
    } catch (err) {
      if (err instanceof ApiError) {
        // Mỗi mã lỗi ở đây là một tình huống khác hẳn nhau, nên nói rõ từng cái
        // thay vì gộp thành "mua thất bại".
        const MESSAGES: Record<string, string> = {
          SOLD_OUT: 'Rất tiếc, sản phẩm vừa được khách khác mua mất',
          FLASH_SALE_LIMIT_EXCEEDED: `Mỗi khách chỉ mua tối đa ${buying.perUserLimit} sản phẩm`,
          RATE_LIMIT_EXCEEDED: 'Bạn thao tác hơi nhanh, chờ một chút rồi thử lại nhé',
          FLASH_SALE_NOT_STARTED: 'Chương trình chưa bắt đầu',
          FLASH_SALE_ENDED: 'Chương trình đã kết thúc',
          SERVICE_UNAVAILABLE: 'Hệ thống đang quá tải, bạn thử lại sau ít phút nhé',
        }
        toast.error(MESSAGES[err.code] ?? err.message, err.code)
      } else {
        toast.showError(err, 'Đặt hàng chưa thành công')
      }
      await load()
    } finally {
      setSubmitting(false)
    }
  }

  if (loading) return <Loading label="Đang tải chương trình…" />

  const renderItem = (item: FlashSaleItemResponse, running: boolean) => {
    const sold = item.saleQuantity - item.remaining
    const soldPercent = item.saleQuantity > 0 ? (sold / item.saleQuantity) * 100 : 0
    const soldOut = item.remaining <= 0

    return (
      <div key={item.id} className="card p-3 flex gap-3">
        <div className="w-24 shrink-0 relative">
          <ProductImage src={item.thumbnailUrl} alt={item.productName} sizes="96px" />
          <span className="absolute top-1 left-1 badge badge-red">-{item.discountPercent}%</span>
        </div>

        <div className="flex-1 min-w-0 flex flex-col gap-1.5">
          <div>
            <p className="font-semibold text-sm text-slate-800 dark:text-slate-100 leading-snug clamp-2">
              {item.productName}
            </p>
            <p className="text-2xs text-slate-500 dark:text-slate-400">{item.variantName}</p>
          </div>

          <div className="flex items-baseline gap-2">
            <span className="price">{formatPrice(item.salePrice)}</span>
            <span className="price-strike">{formatPrice(item.originalPrice)}</span>
          </div>

          <div>
            <div className="h-2 rounded-full bg-slate-200 dark:bg-slate-700 overflow-hidden">
              <div
                className="h-full bg-gradient-to-r from-primary-600 to-primary-500 transition-[width] duration-500"
                style={{ width: `${Math.min(100, soldPercent)}%` }}
              />
            </div>
            <p className="text-2xs text-slate-500 dark:text-slate-400 mt-1">
              Đã bán {sold}/{item.saleQuantity} · tối đa {item.perUserLimit} sản phẩm mỗi khách
            </p>
          </div>

          <button
            onClick={() => openBuy(item)}
            disabled={!running || soldOut}
            className="btn btn-buy btn-sm w-fit mt-0.5"
          >
            {soldOut ? 'Đã bán hết' : running ? 'Mua ngay' : 'Sắp mở bán'}
          </button>
        </div>
      </div>
    )
  }

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="page-title flex items-center gap-2">
          <Zap size={20} className="text-primary-500" /> Flash sale
        </h1>
        <p className="page-desc">
          Giá sốc mỗi ngày, số lượng có hạn. Nhanh tay kẻo lỡ!
        </p>
      </div>

      {active.length === 0 && upcoming.length === 0 && (
        <div className="card">
          <EmptyState
            icon={Zap}
            title="Chưa có chương trình nào đang diễn ra"
            desc="Các chương trình giá sốc sắp tới sẽ hiện ở đây. Bạn quay lại sau nhé!"
          />
        </div>
      )}

      {active.map((sale) => (
        <section key={sale.id} className="card-surface overflow-hidden">
          <header className="flex items-center gap-2.5 px-4 py-3 bg-gradient-to-r from-primary-600 to-primary-500 text-white flex-wrap">
            <Zap size={17} className="shrink-0" />
            <h2 className="font-extrabold text-base">{sale.name}</h2>
            <span className="flex items-center gap-1 ml-auto text-2xs font-bold bg-white/20 px-2 py-1 rounded-md">
              <Clock size={12} />
              Kết thúc sau{' '}
              <Countdown until={sale.endsAt} onExpire={() => void load()} className="text-white" />
            </span>
          </header>

          <div className="p-4 grid gap-3 sm:grid-cols-2">
            {sale.items.map((item) => renderItem(item, true))}
          </div>
        </section>
      ))}

      {upcoming.length > 0 && (
        <section className="flex flex-col gap-3">
          <h2 className="page-title">Sắp diễn ra</h2>
          {upcoming.map((sale) => (
            <div key={sale.id} className="card-surface overflow-hidden">
              <header className="flex items-center gap-2.5 px-4 py-3 border-b border-slate-200 dark:border-surface-border flex-wrap">
                <h3 className="font-bold text-sm text-slate-800 dark:text-slate-100">
                  {sale.name}
                </h3>
                <StatusBadge kind="flashSale" status={sale.status} />
                <span className="text-2xs text-slate-500 dark:text-slate-400 ml-auto flex items-center gap-1.5">
                  Bắt đầu {formatDateTime(sale.startsAt)} · còn{' '}
                  <Countdown seconds={sale.secondsUntilStart} onExpire={() => void load()} />
                </span>
              </header>
              <div className="p-4 grid gap-3 sm:grid-cols-2">
                {sale.items.map((item) => renderItem(item, false))}
              </div>
            </div>
          ))}
        </section>
      )}

      {/* ── Hộp thoại mua hàng ───────────────────────────────────── */}
      <Modal
        open={buying !== null}
        title="Mua ngay với giá sốc"
        onClose={() => setBuying(null)}
        wide
      >
        {buying && (
          <form id="flash-buy-form" onSubmit={submitPurchase} className="flex flex-col gap-4">
            <div className="flex gap-3 p-3 rounded-lg bg-slate-50 dark:bg-surface-card border border-slate-200 dark:border-surface-border">
              <div className="w-16 shrink-0">
                <ProductImage src={buying.thumbnailUrl} alt={buying.productName} sizes="64px" />
              </div>
              <div className="min-w-0">
                <p className="font-semibold text-sm text-slate-800 dark:text-slate-100">
                  {buying.productName}
                </p>
                <p className="text-2xs text-slate-500 dark:text-slate-400">{buying.variantName}</p>
                <p className="price text-sm mt-1">{formatPrice(buying.salePrice)}</p>
              </div>
            </div>

            <div>
              <span className="form-label">Số lượng</span>
              <QuantityStepper
                value={quantity}
                onChange={setQuantity}
                max={Math.min(5, buying.perUserLimit, buying.remaining)}
              />
              <p className="form-hint">
                Còn {buying.remaining} sản phẩm · tối đa {buying.perUserLimit} sản phẩm mỗi khách.
              </p>
            </div>

            <div className="grid gap-3 sm:grid-cols-2">
              <div>
                <label className="form-label" htmlFor="fs-name">
                  Người nhận
                </label>
                <input
                  id="fs-name"
                  value={address.receiverName}
                  onChange={(e) => setAddress({ ...address, receiverName: e.target.value })}
                  required
                  maxLength={150}
                  className="form-input"
                />
              </div>
              <div>
                <label className="form-label" htmlFor="fs-phone">
                  Số điện thoại
                </label>
                <input
                  id="fs-phone"
                  value={address.phone}
                  onChange={(e) => setAddress({ ...address, phone: e.target.value })}
                  required
                  pattern="0\d{9}"
                  title="10 chữ số, bắt đầu bằng 0"
                  placeholder="0901234567"
                  className="form-input"
                />
              </div>
              <div className="sm:col-span-2">
                <label className="form-label" htmlFor="fs-street">
                  Số nhà, tên đường
                </label>
                <input
                  id="fs-street"
                  value={address.street}
                  onChange={(e) => setAddress({ ...address, street: e.target.value })}
                  required
                  maxLength={255}
                  className="form-input"
                />
              </div>
              <div>
                <label className="form-label" htmlFor="fs-ward">
                  Phường / Xã
                </label>
                <input
                  id="fs-ward"
                  value={address.ward}
                  onChange={(e) => setAddress({ ...address, ward: e.target.value })}
                  required
                  maxLength={100}
                  className="form-input"
                />
              </div>
              <div>
                <label className="form-label" htmlFor="fs-district">
                  Quận / Huyện
                </label>
                <input
                  id="fs-district"
                  value={address.district}
                  onChange={(e) => setAddress({ ...address, district: e.target.value })}
                  required
                  maxLength={100}
                  className="form-input"
                />
              </div>
              <div className="sm:col-span-2">
                <label className="form-label" htmlFor="fs-province">
                  Tỉnh / Thành phố
                </label>
                <input
                  id="fs-province"
                  value={address.province}
                  onChange={(e) => setAddress({ ...address, province: e.target.value })}
                  required
                  maxLength={100}
                  className="form-input"
                />
              </div>
            </div>

            <div className="flex items-center justify-between gap-3 pt-3 border-t border-slate-100 dark:border-slate-700">
              <div>
                <p className="text-2xs text-slate-500 dark:text-slate-400">Tổng tiền</p>
                <p className="price text-lg">{formatPrice(buying.salePrice * quantity)}</p>
              </div>
              <button type="submit" disabled={submitting} className="btn btn-buy px-6">
                {submitting ? (
                  <>
                    <Loader2 size={15} className="animate-spin" /> Đang đặt hàng…
                  </>
                ) : (
                  'Xác nhận mua'
                )}
              </button>
            </div>
          </form>
        )}
      </Modal>
    </div>
  )
}
