'use client'

import { useCallback, useEffect, useRef, useState } from 'react'
import Link from 'next/link'
import { useRouter } from 'next/navigation'
import {
  AlertTriangle,
  BadgePercent,
  CheckCircle2,
  Loader2,
  PackageX,
  RotateCcw,
  Timer,
} from 'lucide-react'
import * as checkoutApi from '@/lib/api/checkout'
import * as accountApi from '@/lib/api/account'
import * as couponApi from '@/lib/api/coupon'
import * as orderApi from '@/lib/api/order'
import Countdown from '@/components/Countdown'
import { EmptyState, Loading } from '@/components/Feedback'
import { ApiError, newIdempotencyKey } from '@/lib/api/client'
import { useAuth } from '@/contexts/AuthContext'
import { useCart } from '@/contexts/CartContext'
import { useToast } from '@/contexts/ToastContext'
import { formatPrice } from '@/lib/format'
import type {
  AddressResponse,
  CheckoutSessionResponse,
  ShippingAddress,
  StockShortage,
  ValidateCouponResponse,
} from '@/types'

/**
 * Phí vận chuyển phẳng - phải khớp OrderService.FLAT_SHIPPING_FEE ở backend.
 * Bản trước ghi "Miễn phí" và không cộng vào tổng: khách thấy một số, bị trừ một số khác.
 */
const SHIPPING_FEE = 30_000

function toShipping(a: AddressResponse): ShippingAddress {
  return {
    receiverName: a.receiverName,
    phone: a.phone,
    street: a.street,
    ward: a.ward,
    district: a.district,
    province: a.province,
  }
}

const EMPTY_ADDRESS: ShippingAddress = {
  receiverName: '',
  phone: '',
  street: '',
  ward: '',
  district: '',
  province: '',
}

export default function CheckoutView() {
  const router = useRouter()
  const { user } = useAuth()
  const { cart, refresh: refreshCart } = useCart()
  const toast = useToast()

  const [session, setSession] = useState<CheckoutSessionResponse | null>(null)
  const [holding, setHolding] = useState(true)
  const [expired, setExpired] = useState(false)
  const [shortages, setShortages] = useState<StockShortage[] | null>(null)
  const [holdError, setHoldError] = useState<string | null>(null)

  const [address, setAddress] = useState<ShippingAddress>(EMPTY_ADDRESS)
  const [savedAddresses, setSavedAddresses] = useState<AddressResponse[]>([])
  const [pickedAddressId, setPickedAddressId] = useState<number | ''>('')
  const [saveToBook, setSaveToBook] = useState(false)
  const [note, setNote] = useState('')
  const [couponCode, setCouponCode] = useState('')
  const [coupon, setCoupon] = useState<ValidateCouponResponse | null>(null)
  const [checkingCoupon, setCheckingCoupon] = useState(false)
  const [placing, setPlacing] = useState(false)

  /**
   * Idempotency-Key sinh MỘT lần cho cả lượt đặt hàng này và giữ nguyên qua mọi
   * lần bấm lại. Nếu sinh key mới mỗi lần bấm, khách bấm hai lần vì mạng chậm sẽ
   * ra hai đơn — đúng thứ mà header này sinh ra để chặn.
   */
  const idempotencyKey = useRef(newIdempotencyKey())

  // Điền sẵn tên người nhận từ hồ sơ; số điện thoại chỉ điền khi hợp lệ.
  useEffect(() => {
    if (!user) return
    setAddress((a) => ({
      ...a,
      receiverName: a.receiverName || user.fullName || '',
      phone: a.phone || (user.phone && /^0\d{9}$/.test(user.phone) ? user.phone : ''),
    }))
  }, [user])

  // Sổ địa chỉ: có địa chỉ mặc định thì điền sẵn luôn - khách chỉ việc bấm đặt hàng.
  useEffect(() => {
    if (user?.role !== 'CUSTOMER') return
    accountApi
      .listAddresses()
      .then((list) => {
        setSavedAddresses(list)
        const def = list.find((a) => a.isDefault)
        if (def) {
          setPickedAddressId(def.id)
          setAddress(toShipping(def))
        }
      })
      .catch(() => undefined)
  }, [user])

  const pickAddress = (id: number | '') => {
    setPickedAddressId(id)
    const a = savedAddresses.find((x) => x.id === id)
    setAddress(a ? toShipping(a) : { ...EMPTY_ADDRESS, receiverName: user?.fullName ?? '' })
  }

  const editAddress = (patch: Partial<ShippingAddress>) => {
    // Sửa tay thì không còn là địa chỉ đã lưu nữa
    setPickedAddressId('')
    setAddress((a) => ({ ...a, ...patch }))
  }

  const startHold = useCallback(async () => {
    setHolding(true)
    setExpired(false)
    setShortages(null)
    setHoldError(null)
    try {
      setSession(await checkoutApi.createCheckoutSession())
    } catch (err) {
      if (err instanceof ApiError) {
        if (err.code === 'INSUFFICIENT_STOCK') {
          // `details` liệt kê từng variant thiếu bao nhiêu — chỉ đúng dòng có vấn đề.
          setShortages(Array.isArray(err.details) ? (err.details as StockShortage[]) : [])
        } else {
          setHoldError(err.message)
        }
      } else {
        setHoldError('Không giữ được hàng, vui lòng thử lại')
      }
    } finally {
      setHolding(false)
    }
  }, [])

  /**
   * Chỉ giữ hàng MỘT lần khi vào trang.
   *
   * Ở chế độ dev, StrictMode của React gọi effect hai lần. Không chặn thì lần
   * thứ hai tạo thêm một phiên giữ hàng nữa: hoặc giữ gấp đôi số hàng, hoặc
   * chính nó đụng 409 INSUFFICIENT_STOCK vì phiên đầu vừa giữ mất hàng.
   * Các nút "Thử lại"/"Đặt lại" vẫn gọi startHold() trực tiếp được.
   */
  const holdStarted = useRef(false)

  useEffect(() => {
    if (holdStarted.current) return
    holdStarted.current = true
    void startHold()
  }, [startHold])

  const applyCoupon = async () => {
    const code = couponCode.trim().toUpperCase()
    if (!code || !session) return
    setCheckingCoupon(true)
    try {
      const res = await couponApi.validateCoupon(code, session.subtotal)
      setCoupon(res)
      if (res.valid) toast.success(`Áp dụng ${code}: giảm ${formatPrice(res.discountAmount)}`)
      else toast.error(res.message, res.reason ?? undefined)
    } catch (err) {
      setCoupon(null)
      toast.showError(err, 'Không kiểm tra được mã giảm giá')
    } finally {
      setCheckingCoupon(false)
    }
  }

  const placeOrder = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!session || expired) return

    setPlacing(true)
    try {
      const order = await orderApi.placeOrder(
        {
          sessionId: session.sessionId,
          shippingAddress: address,
          note: note.trim() || undefined,
          couponCode: coupon?.valid ? couponCode.trim().toUpperCase() : undefined,
        },
        idempotencyKey.current,
      )
      await refreshCart()
      // Lưu sổ địa chỉ SAU khi đơn đã tạo, và lỗi ở đây không được làm hỏng một đơn đã thành công
      if (saveToBook && pickedAddressId === '') {
        await accountApi
          .createAddress({ ...address, makeDefault: savedAddresses.length === 0 })
          .catch(() => undefined)
      }
      toast.success(`Đặt hàng thành công — mã đơn ${order.code}`)
      router.replace(`/orders/${order.code}`)
    } catch (err) {
      if (err instanceof ApiError) {
        // Mã giảm giá "hợp lệ" lúc kiểm tra vẫn có thể hết lượt đúng lúc đặt đơn:
        // lượt thật chỉ bị trừ trong transaction đặt hàng.
        if (err.code === 'COUPON_USED_UP' || err.code === 'COUPON_USER_LIMIT_EXCEEDED') {
          setCoupon(null)
          toast.error(`${err.message}. Bỏ mã và thử đặt lại.`, err.code)
        } else if (err.code === 'RESERVATION_EXPIRED' || err.code === 'RESERVATION_NOT_FOUND') {
          setExpired(true)
          toast.error('Đã hết thời gian giữ chỗ', err.code)
        } else {
          toast.showError(err)
        }
      } else {
        toast.showError(err, 'Đặt hàng thất bại')
      }
      setPlacing(false)
    }
  }

  const abandon = async () => {
    if (session) await checkoutApi.cancelCheckoutSession(session.sessionId).catch(() => undefined)
    router.push('/cart')
  }

  // ── Các trạng thái chưa vào được biểu mẫu ──────────────────────────────

  if (holding) return <Loading label="Đang chuẩn bị đơn hàng…" />

  if (shortages) {
    return (
      <div className="card-padded max-w-lg mx-auto">
        <div className="flex items-start gap-2.5 mb-4">
          <AlertTriangle size={20} className="text-amber-500 shrink-0 mt-0.5" />
          <div>
            <h1 className="text-base font-bold text-slate-800 dark:text-slate-100">
              Sản phẩm vừa hết hàng
            </h1>
            <p className="text-sm text-slate-500 dark:text-slate-400 mt-0.5">
              Rất tiếc, sản phẩm dưới đây vừa được khách khác đặt mất. Bạn giảm số lượng
              rồi thử lại giúp shop nhé.
            </p>
          </div>
        </div>

        {shortages.length > 0 && (
          <ul className="flex flex-col gap-2 mb-4">
            {shortages.map((s) => (
              <li
                key={s.variantId}
                className="flex items-center justify-between gap-3 px-3 py-2.5 rounded-lg bg-slate-50 dark:bg-surface-card border border-slate-200 dark:border-surface-border"
              >
                <div className="min-w-0">
                  <p className="text-sm font-semibold text-slate-800 dark:text-slate-100 truncate">
                    {s.productName}
                  </p>
                  <p className="text-2xs text-slate-500 dark:text-slate-400 font-mono">{s.sku}</p>
                </div>
                <p className="text-2xs text-right shrink-0">
                  <span className="text-slate-500 dark:text-slate-400">Bạn đặt </span>
                  <span className="font-bold text-slate-700 dark:text-slate-200">
                    {s.requested}
                  </span>
                  <br />
                  <span className="text-slate-500 dark:text-slate-400">Còn </span>
                  <span className="font-bold text-amber-600 dark:text-amber-400">
                    {s.available}
                  </span>
                </p>
              </li>
            ))}
          </ul>
        )}

        <div className="flex gap-2">
          <Link href="/cart" className="btn btn-primary flex-1">
            Sửa giỏ hàng
          </Link>
          <button onClick={() => void startHold()} className="btn btn-secondary">
            <RotateCcw size={14} /> Thử lại
          </button>
        </div>
      </div>
    )
  }

  if (holdError || !session) {
    return (
      <div className="card">
        <EmptyState
          icon={PackageX}
          title="Chưa thể thanh toán"
          desc={holdError ?? 'Giỏ hàng đang trống. Bạn chọn thêm sản phẩm rồi quay lại nhé.'}
          action={
            <div className="flex gap-2">
              <Link href="/cart" className="btn btn-secondary">
                Về giỏ hàng
              </Link>
              <button onClick={() => void startHold()} className="btn btn-primary">
                <RotateCcw size={14} /> Thử lại
              </button>
            </div>
          }
        />
      </div>
    )
  }

  const discount = coupon?.valid ? coupon.discountAmount : 0
  const estimatedTotal = Math.max(0, session.subtotal + SHIPPING_FEE - discount)

  return (
    <div className="flex flex-col gap-4">
      <div className="flex items-center justify-between gap-3 flex-wrap">
        <div>
          <h1 className="page-title">Thanh toán</h1>
          <p className="page-desc">{cart?.totalItems ?? session.items.length} sản phẩm</p>
        </div>

        <div
          className={`flex items-center gap-2 px-3 py-2 rounded-lg border text-sm ${
            expired
              ? 'bg-red-50 border-red-200 text-red-700 dark:bg-red-900/25 dark:border-red-800 dark:text-red-300'
              : 'bg-primary-50 border-primary-200 text-primary-700 dark:bg-primary-900/25 dark:border-primary-800 dark:text-primary-300'
          }`}
        >
          <Timer size={15} className="shrink-0" />
          {expired ? (
            <span className="font-semibold">Đã hết thời gian giữ chỗ</span>
          ) : (
            <>
              <span>Hoàn tất trong</span>
              <Countdown
                seconds={session.secondsRemaining}
                onExpire={() => setExpired(true)}
              />
            </>
          )}
        </div>
      </div>

      {expired && (
        <div className="card-padded flex flex-col sm:flex-row sm:items-center gap-3">
          <AlertTriangle size={18} className="text-red-500 shrink-0" />
          <p className="text-sm text-slate-600 dark:text-slate-300 flex-1 leading-relaxed">
            Đơn hàng của bạn không còn được giữ chỗ nữa. Bấm đặt lại để tiếp tục mua —
            giá và số lượng có thể đã thay đổi.
          </p>
          <button onClick={() => void startHold()} className="btn btn-primary shrink-0">
            <RotateCcw size={14} /> Đặt lại
          </button>
        </div>
      )}

      <form
        onSubmit={placeOrder}
        className="grid gap-4 lg:grid-cols-[minmax(0,1fr)_20rem] items-start"
      >
        <div className="flex flex-col gap-4">
          {/* ── Địa chỉ giao hàng ────────────────────────────────── */}
          <section className="card-padded">
            <div className="flex items-center justify-between gap-3 flex-wrap mb-4">
              <h2 className="text-base font-bold text-slate-800 dark:text-slate-100">
                Địa chỉ giao hàng
              </h2>
              {savedAddresses.length > 0 && (
                <select
                  value={pickedAddressId}
                  onChange={(e) => pickAddress(e.target.value ? Number(e.target.value) : '')}
                  aria-label="Chọn từ sổ địa chỉ"
                  className="form-select py-1.5 text-2xs w-auto max-w-64"
                >
                  <option value="">Nhập địa chỉ khác…</option>
                  {savedAddresses.map((a) => (
                    <option key={a.id} value={a.id}>
                      {(a.label || a.receiverName) + ' — ' + a.street + ', ' + a.district}
                    </option>
                  ))}
                </select>
              )}
            </div>

            <div className="grid gap-3 sm:grid-cols-2">
              <div>
                <label className="form-label" htmlFor="co-name">
                  Người nhận
                </label>
                <input
                  id="co-name"
                  value={address.receiverName}
                  onChange={(e) => editAddress({ receiverName: e.target.value })}
                  required
                  maxLength={150}
                  placeholder="Nguyễn Văn A"
                  className="form-input"
                />
              </div>

              <div>
                <label className="form-label" htmlFor="co-phone">
                  Số điện thoại
                </label>
                <input
                  id="co-phone"
                  value={address.phone}
                  onChange={(e) => editAddress({ phone: e.target.value })}
                  required
                  pattern="0\d{9}"
                  title="10 chữ số, bắt đầu bằng 0"
                  placeholder="0901234567"
                  className="form-input"
                />
              </div>

              <div className="sm:col-span-2">
                <label className="form-label" htmlFor="co-street">
                  Số nhà, tên đường
                </label>
                <input
                  id="co-street"
                  value={address.street}
                  onChange={(e) => editAddress({ street: e.target.value })}
                  required
                  maxLength={255}
                  placeholder="123 Nguyễn Huệ"
                  className="form-input"
                />
              </div>

              <div>
                <label className="form-label" htmlFor="co-ward">
                  Phường / Xã
                </label>
                <input
                  id="co-ward"
                  value={address.ward}
                  onChange={(e) => editAddress({ ward: e.target.value })}
                  required
                  maxLength={100}
                  placeholder="Phường Bến Nghé"
                  className="form-input"
                />
              </div>

              <div>
                <label className="form-label" htmlFor="co-district">
                  Quận / Huyện
                </label>
                <input
                  id="co-district"
                  value={address.district}
                  onChange={(e) => editAddress({ district: e.target.value })}
                  required
                  maxLength={100}
                  placeholder="Quận 1"
                  className="form-input"
                />
              </div>

              <div className="sm:col-span-2">
                <label className="form-label" htmlFor="co-province">
                  Tỉnh / Thành phố
                </label>
                <input
                  id="co-province"
                  value={address.province}
                  onChange={(e) => editAddress({ province: e.target.value })}
                  required
                  maxLength={100}
                  placeholder="TP. Hồ Chí Minh"
                  className="form-input"
                />
              </div>

              {pickedAddressId === '' && savedAddresses.length < 10 && (
                <label className="sm:col-span-2 flex items-center gap-2 text-sm text-slate-600 dark:text-slate-300">
                  <input type="checkbox" checked={saveToBook} onChange={(e) => setSaveToBook(e.target.checked)} />
                  Lưu địa chỉ này vào sổ địa chỉ
                </label>
              )}

              <div className="sm:col-span-2">
                <label className="form-label" htmlFor="co-note">
                  Ghi chú
                </label>
                <textarea
                  id="co-note"
                  value={note}
                  onChange={(e) => setNote(e.target.value)}
                  maxLength={500}
                  rows={2}
                  placeholder="Giao giờ hành chính, gọi trước khi tới…"
                  className="form-input resize-none"
                />
              </div>
            </div>
          </section>

          {/* ── Hàng đang giữ ────────────────────────────────────── */}
          <section className="card-padded">
            <h2 className="text-base font-bold text-slate-800 dark:text-slate-100 mb-3">
              Sản phẩm trong đơn
            </h2>
            <ul className="flex flex-col divide-y divide-slate-100 dark:divide-slate-700">
              {session.items.map((line) => (
                <li key={line.variantId} className="py-2.5 first:pt-0 flex gap-3">
                  <div className="flex-1 min-w-0">
                    <p className="text-sm font-semibold text-slate-800 dark:text-slate-100">
                      {line.productName}
                    </p>
                    <p className="text-2xs text-slate-500 dark:text-slate-400">
                      {line.variantName} · <span className="font-mono">{line.sku}</span> ×{' '}
                      {line.quantity}
                    </p>
                  </div>
                  <p className="text-sm font-bold text-slate-800 dark:text-slate-100 tabular-nums shrink-0">
                    {formatPrice(line.lineTotal)}
                  </p>
                </li>
              ))}
            </ul>
          </section>
        </div>

        {/* ── Cột tổng kết ────────────────────────────────────────── */}
        <aside className="card-padded lg:sticky lg:top-20 flex flex-col gap-3">
          <h2 className="text-base font-bold text-slate-800 dark:text-slate-100">
            Tổng kết đơn hàng
          </h2>

          <div>
            <label className="form-label" htmlFor="co-coupon">
              Mã giảm giá
            </label>
            <div className="flex gap-2">
              <input
                id="co-coupon"
                value={couponCode}
                onChange={(e) => {
                  setCouponCode(e.target.value.toUpperCase())
                  setCoupon(null)
                }}
                placeholder="SALE10"
                className="form-input font-mono"
              />
              <button
                type="button"
                onClick={() => void applyCoupon()}
                disabled={checkingCoupon || !couponCode.trim()}
                className="btn btn-secondary shrink-0"
              >
                {checkingCoupon ? <Loader2 size={14} className="animate-spin" /> : 'Áp dụng'}
              </button>
            </div>

            {coupon && (
              <p
                className={`text-2xs mt-1.5 flex items-center gap-1 ${
                  coupon.valid
                    ? 'text-emerald-600 dark:text-emerald-400'
                    : 'text-red-600 dark:text-red-400'
                }`}
              >
                {coupon.valid ? <CheckCircle2 size={12} /> : <AlertTriangle size={12} />}
                {coupon.message}
              </p>
            )}

            <p className="form-hint">
              Mã sẽ được áp dụng chính thức khi bạn đặt hàng.
            </p>
          </div>

          <dl className="flex flex-col gap-1.5 text-sm pt-3 border-t border-slate-100 dark:border-slate-700">
            <div className="flex justify-between">
              <dt className="text-slate-500 dark:text-slate-400">Tạm tính</dt>
              <dd className="font-semibold text-slate-800 dark:text-slate-100 tabular-nums">
                {formatPrice(session.subtotal)}
              </dd>
            </div>

            {discount > 0 && (
              <div className="flex justify-between">
                <dt className="text-slate-500 dark:text-slate-400 flex items-center gap-1">
                  <BadgePercent size={13} /> Giảm giá
                </dt>
                <dd className="font-semibold text-emerald-600 dark:text-emerald-400 tabular-nums">
                  −{formatPrice(discount)}
                </dd>
              </div>
            )}

            <div className="flex justify-between">
              <dt className="text-slate-500 dark:text-slate-400">Phí vận chuyển</dt>
              <dd className="font-semibold text-slate-800 dark:text-slate-100 tabular-nums">
                {formatPrice(SHIPPING_FEE)}
              </dd>
            </div>
          </dl>

          <div className="flex justify-between items-baseline pt-3 border-t border-slate-100 dark:border-slate-700">
            <span className="font-bold text-slate-800 dark:text-slate-100">Tổng cộng</span>
            <span className="price text-lg">{formatPrice(estimatedTotal)}</span>
          </div>

          <button
            type="submit"
            disabled={placing || expired}
            className="btn btn-buy w-full py-3"
          >
            {placing ? (
              <>
                <Loader2 size={16} className="animate-spin" /> Đang đặt hàng…
              </>
            ) : (
              'Đặt hàng'
            )}
          </button>

          <button
            type="button"
            onClick={() => void abandon()}
            disabled={placing}
            className="btn btn-ghost w-full"
          >
            Quay lại giỏ hàng
          </button>
        </aside>
      </form>
    </div>
  )
}
