'use client'

import { useState } from 'react'
import Link from 'next/link'
import { useRouter } from 'next/navigation'
import { AlertTriangle, Loader2, ShoppingCart, Trash2 } from 'lucide-react'
import ProductImage from '@/components/ProductImage'
import QuantityStepper from '@/components/QuantityStepper'
import { EmptyState, Loading } from '@/components/Feedback'
import { useCart } from '@/contexts/CartContext'
import { useToast } from '@/contexts/ToastContext'
import { formatPrice } from '@/lib/format'

export default function CartView() {
  const router = useRouter()
  const { cart, isLoading, updateItem, removeItem, clear } = useCart()
  const toast = useToast()

  // Khoá riêng từng dòng thay vì khoá cả giỏ: sửa dòng này không nên
  // làm đơ nút xoá của dòng khác.
  const [busyItemId, setBusyItemId] = useState<number | null>(null)
  const [clearing, setClearing] = useState(false)

  const runOnItem = async (itemId: number, fn: () => Promise<unknown>, errorMsg: string) => {
    setBusyItemId(itemId)
    try {
      await fn()
    } catch (err) {
      toast.showError(err, errorMsg)
    } finally {
      setBusyItemId(null)
    }
  }

  if (isLoading && !cart) return <Loading label="Đang tải giỏ hàng…" />

  if (!cart || cart.items.length === 0) {
    return (
      <div className="card">
        <EmptyState
          icon={ShoppingCart}
          title="Giỏ hàng đang trống"
          desc="Thêm vài sản phẩm để bắt đầu đặt hàng."
          action={
            <Link href="/products" className="btn btn-primary">
              Xem sản phẩm
            </Link>
          }
        />
      </div>
    )
  }

  return (
    <div className="flex flex-col gap-4">
      <div className="flex items-center justify-between gap-3">
        <div>
          <h1 className="page-title">Giỏ hàng</h1>
          <p className="page-desc">{cart.totalItems} sản phẩm</p>
        </div>
        <button
          onClick={async () => {
            setClearing(true)
            try {
              await clear()
            } catch (err) {
              toast.showError(err, 'Không xoá được giỏ hàng')
            } finally {
              setClearing(false)
            }
          }}
          disabled={clearing}
          className="btn btn-danger btn-sm"
        >
          {clearing ? <Loader2 size={13} className="animate-spin" /> : <Trash2 size={13} />}
          Xoá tất cả
        </button>
      </div>

      {cart.hasStockIssue && (
        <div className="flex items-start gap-2 px-3.5 py-3 rounded-xl text-sm bg-amber-50 border border-amber-200 text-amber-800 dark:bg-amber-900/25 dark:border-amber-800/60 dark:text-amber-200">
          <AlertTriangle size={16} className="shrink-0 mt-0.5" />
          <p className="leading-relaxed">
            Một vài sản phẩm trong giỏ đang bán rất chạy và không còn đủ số lượng bạn
            chọn. Bạn giảm bớt số lượng giúp shop để tiếp tục đặt hàng nhé.
          </p>
        </div>
      )}

      <div className="grid gap-4 lg:grid-cols-[minmax(0,1fr)_20rem] items-start">
        <ul className="flex flex-col gap-2.5">
          {cart.items.map((item) => {
            const busy = busyItemId === item.id
            return (
              <li
                key={item.id}
                className={`card p-3 flex gap-3 ${item.exceedsAvailable ? 'border-amber-300 dark:border-amber-700' : ''}`}
              >
                <Link href="/products" className="w-20 shrink-0">
                  <ProductImage src={item.thumbnailUrl} alt={item.productName} sizes="80px" />
                </Link>

                <div className="flex-1 min-w-0 flex flex-col gap-1">
                  <p className="font-semibold text-sm text-slate-800 dark:text-slate-100 leading-snug">
                    {item.productName}
                  </p>
                  <p className="text-2xs text-slate-500 dark:text-slate-400">
                    {item.variantName} · <span className="font-mono">{item.sku}</span>
                  </p>

                  {item.exceedsAvailable && (
                    <p className="text-2xs font-semibold text-amber-700 dark:text-amber-400">
                      Chỉ còn {item.availableQuantity} máy — hãy giảm số lượng
                    </p>
                  )}

                  <div className="flex items-center gap-2 flex-wrap mt-1">
                    <QuantityStepper
                      value={item.quantity}
                      max={item.availableQuantity}
                      disabled={busy}
                      onChange={(q) => {
                        if (q === item.quantity) return
                        void runOnItem(
                          item.id,
                          () => updateItem(item.id, q),
                          'Không đổi được số lượng',
                        )
                      }}
                    />

                    <button
                      onClick={() =>
                        void runOnItem(item.id, () => removeItem(item.id), 'Không xoá được dòng này')
                      }
                      disabled={busy}
                      className="btn btn-danger btn-sm"
                      aria-label={`Xoá ${item.productName}`}
                    >
                      {busy ? (
                        <Loader2 size={13} className="animate-spin" />
                      ) : (
                        <Trash2 size={13} />
                      )}
                    </button>

                    <div className="ml-auto text-right">
                      <p className="price text-sm">{formatPrice(item.lineTotal)}</p>
                      <p className="text-2xs text-slate-500 dark:text-slate-400">
                        {formatPrice(item.unitPrice)} × {item.quantity}
                      </p>
                    </div>
                  </div>
                </div>
              </li>
            )
          })}
        </ul>

        <aside className="card-padded lg:sticky lg:top-20 flex flex-col gap-3">
          <h2 className="text-base font-bold text-slate-800 dark:text-slate-100">
            Tóm tắt đơn hàng
          </h2>

          <dl className="flex flex-col gap-1.5 text-sm">
            <div className="flex justify-between">
              <dt className="text-slate-500 dark:text-slate-400">Tạm tính</dt>
              <dd className="font-semibold text-slate-800 dark:text-slate-100 tabular-nums">
                {formatPrice(cart.subtotal)}
              </dd>
            </div>
            <div className="flex justify-between">
              <dt className="text-slate-500 dark:text-slate-400">Phí vận chuyển</dt>
              <dd className="text-2xs text-slate-500 dark:text-slate-400">
                Tính ở bước thanh toán
              </dd>
            </div>
          </dl>

          <div className="flex justify-between items-baseline pt-3 border-t border-slate-100 dark:border-slate-700">
            <span className="font-bold text-slate-800 dark:text-slate-100">Tổng cộng</span>
            <span className="price text-lg">{formatPrice(cart.subtotal)}</span>
          </div>

          <button
            onClick={() => router.push('/checkout')}
            disabled={cart.hasStockIssue}
            className="btn btn-buy w-full py-3"
          >
            Tiến hành thanh toán
          </button>

          {cart.hasStockIssue && (
            <p className="text-2xs text-amber-700 dark:text-amber-400 text-center">
              Vui lòng điều chỉnh số lượng ở trên trước
            </p>
          )}

          <p className="text-2xs text-slate-500 dark:text-slate-400 leading-relaxed">
            Shop giữ chỗ đơn hàng của bạn trong 10 phút để hoàn tất thanh toán.
          </p>
        </aside>
      </div>
    </div>
  )
}
