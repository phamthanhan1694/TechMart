'use client'

import { useCallback, useEffect, useState } from 'react'
import { BadgePercent, Loader2, Plus, Power } from 'lucide-react'
import * as couponApi from '@/lib/api/coupon'
import Modal from '@/components/Modal'
import { EmptyState, ErrorBox, Loading } from '@/components/Feedback'
import { ApiError } from '@/lib/api/client'
import { useToast } from '@/contexts/ToastContext'
import { formatDateTime, formatNumber, formatPrice, toDateTimeLocalInput } from '@/lib/format'
import type { CouponResponse, DiscountType } from '@/types'

export default function AdminCouponsView() {
  const toast = useToast()

  const [coupons, setCoupons] = useState<CouponResponse[]>([])
  // Mốc thời gian chốt lúc nạp danh sách. Đọc đồng hồ ngay trong render sẽ làm
  // render không tất định — cùng props mà ra kết quả khác nhau.
  const [loadedAt, setLoadedAt] = useState(0)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<{ message: string; code?: string } | null>(null)

  const [open, setOpen] = useState(false)
  const [saving, setSaving] = useState(false)
  const [form, setForm] = useState({
    code: '',
    type: 'PERCENT' as DiscountType,
    value: '',
    minOrderAmount: '',
    maxDiscount: '',
    usageLimit: '',
    perUserLimit: '',
    startsAt: '',
    endsAt: '',
  })

  const load = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      setCoupons(await couponApi.listCoupons())
      setLoadedAt(Date.now())
    } catch (err) {
      setError(
        err instanceof ApiError
          ? { message: err.message, code: err.code }
          : { message: 'Không tải được danh sách mã giảm giá' },
      )
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    void load()
  }, [load])

  const openCreate = () => {
    const now = new Date()
    const in30Days = new Date()
    in30Days.setDate(in30Days.getDate() + 30)
    setForm({
      code: '',
      type: 'PERCENT',
      value: '',
      minOrderAmount: '',
      maxDiscount: '',
      usageLimit: '',
      perUserLimit: '',
      startsAt: toDateTimeLocalInput(now),
      endsAt: toDateTimeLocalInput(in30Days),
    })
    setOpen(true)
  }

  const create = async (e: React.FormEvent) => {
    e.preventDefault()
    setSaving(true)
    try {
      await couponApi.createCoupon({
        code: form.code.trim().toUpperCase(),
        type: form.type,
        value: Number(form.value),
        minOrderAmount: form.minOrderAmount ? Number(form.minOrderAmount) : undefined,
        // Trần giảm giá chỉ có nghĩa với mã theo phần trăm.
        maxDiscount:
          form.type === 'PERCENT' && form.maxDiscount ? Number(form.maxDiscount) : undefined,
        usageLimit: form.usageLimit ? Number(form.usageLimit) : undefined,
        perUserLimit: form.perUserLimit ? Number(form.perUserLimit) : undefined,
        startsAt: new Date(form.startsAt).toISOString(),
        endsAt: new Date(form.endsAt).toISOString(),
      })
      setOpen(false)
      toast.success('Đã tạo mã giảm giá')
      await load()
    } catch (err) {
      toast.showError(err, 'Không tạo được mã giảm giá')
    } finally {
      setSaving(false)
    }
  }

  const deactivate = async (c: CouponResponse) => {
    if (!window.confirm(`Vô hiệu hoá mã ${c.code}? Mã đã dùng vẫn nằm trong lịch sử đơn.`)) return
    try {
      await couponApi.deactivateCoupon(c.id)
      toast.success(`Đã vô hiệu hoá ${c.code}`)
      await load()
    } catch (err) {
      toast.showError(err, 'Không vô hiệu hoá được mã')
    }
  }

  return (
    <div className="flex flex-col gap-4">
      <div className="flex items-end justify-between gap-3 flex-wrap">
        <div>
          <h1 className="page-title">Mã giảm giá</h1>
          <p className="page-desc">Lượt dùng chỉ bị trừ trong transaction đặt hàng, có khoá dòng</p>
        </div>
        <button onClick={openCreate} className="btn btn-primary btn-sm">
          <Plus size={14} /> Tạo mã
        </button>
      </div>

      {error && <ErrorBox message={error.message} code={error.code} />}

      <div className="card">
        {loading ? (
          <Loading />
        ) : coupons.length === 0 ? (
          <EmptyState icon={BadgePercent} title="Chưa có mã giảm giá nào" />
        ) : (
          <div className="overflow-x-auto">
            <table className="data-table">
              <thead>
                <tr>
                  <th>Mã</th>
                  <th>Giảm</th>
                  <th>Đơn tối thiểu</th>
                  <th className="text-right">Lượt dùng</th>
                  <th>Hiệu lực</th>
                  <th>Trạng thái</th>
                  <th />
                </tr>
              </thead>
              <tbody>
                {coupons.map((c) => {
                  const expired = new Date(c.endsAt).getTime() < loadedAt
                  const usedUp = c.usageLimit != null && c.usedCount >= c.usageLimit
                  return (
                    <tr key={c.id}>
                      <td className="font-mono font-bold text-slate-800 dark:text-slate-100">
                        {c.code}
                      </td>
                      <td className="whitespace-nowrap">
                        {c.type === 'PERCENT' ? (
                          <>
                            {c.value}%
                            {c.maxDiscount != null && (
                              <span className="text-2xs text-slate-500 dark:text-slate-400">
                                {' '}
                                (tối đa {formatPrice(c.maxDiscount)})
                              </span>
                            )}
                          </>
                        ) : (
                          formatPrice(c.value)
                        )}
                      </td>
                      <td className="tabular-nums whitespace-nowrap">
                        {c.minOrderAmount ? formatPrice(c.minOrderAmount) : '—'}
                      </td>
                      <td className="text-right tabular-nums">
                        {formatNumber(c.usedCount)}
                        {c.usageLimit != null && (
                          <span className="text-slate-400"> / {formatNumber(c.usageLimit)}</span>
                        )}
                      </td>
                      <td className="text-2xs text-slate-500 dark:text-slate-400 whitespace-nowrap">
                        {formatDateTime(c.startsAt)}
                        <br />
                        {formatDateTime(c.endsAt)}
                      </td>
                      <td>
                        {!c.active ? (
                          <span className="badge badge-slate">Đã tắt</span>
                        ) : expired ? (
                          <span className="badge badge-amber">Hết hạn</span>
                        ) : usedUp ? (
                          <span className="badge badge-amber">Hết lượt</span>
                        ) : (
                          <span className="badge badge-emerald">Đang chạy</span>
                        )}
                      </td>
                      <td className="text-right">
                        {c.active && (
                          <button
                            onClick={() => void deactivate(c)}
                            className="btn btn-danger btn-sm"
                            aria-label={`Vô hiệu hoá ${c.code}`}
                          >
                            <Power size={13} />
                          </button>
                        )}
                      </td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </div>
        )}
      </div>

      <Modal open={open} title="Tạo mã giảm giá" onClose={() => setOpen(false)} wide>
        <form onSubmit={create} className="grid gap-3 sm:grid-cols-2">
          <div>
            <label className="form-label" htmlFor="ac-code">
              Mã
            </label>
            <input
              id="ac-code"
              value={form.code}
              onChange={(e) => setForm({ ...form, code: e.target.value.toUpperCase() })}
              required
              maxLength={50}
              pattern="[A-Z0-9_-]+"
              title="Chỉ chữ hoa, số, gạch ngang và gạch dưới"
              placeholder="SALE10"
              className="form-input font-mono"
            />
          </div>

          <div>
            <label className="form-label" htmlFor="ac-type">
              Kiểu giảm
            </label>
            <select
              id="ac-type"
              value={form.type}
              onChange={(e) => setForm({ ...form, type: e.target.value as DiscountType })}
              className="form-select"
            >
              <option value="PERCENT">Theo phần trăm</option>
              <option value="FIXED">Số tiền cố định</option>
            </select>
          </div>

          <div>
            <label className="form-label" htmlFor="ac-value">
              {form.type === 'PERCENT' ? 'Phần trăm giảm (%)' : 'Số tiền giảm (₫)'}
            </label>
            <input
              id="ac-value"
              type="number"
              min={0.01}
              max={form.type === 'PERCENT' ? 100 : undefined}
              step={form.type === 'PERCENT' ? 1 : 1000}
              value={form.value}
              onChange={(e) => setForm({ ...form, value: e.target.value })}
              required
              className="form-input"
            />
          </div>

          <div>
            <label className="form-label" htmlFor="ac-max">
              Trần giảm giá (₫)
            </label>
            <input
              id="ac-max"
              type="number"
              min={0.01}
              step={1000}
              value={form.maxDiscount}
              onChange={(e) => setForm({ ...form, maxDiscount: e.target.value })}
              disabled={form.type !== 'PERCENT'}
              className="form-input"
            />
            <p className="form-hint">Chỉ áp dụng cho mã theo phần trăm.</p>
          </div>

          <div>
            <label className="form-label" htmlFor="ac-min">
              Giá trị đơn tối thiểu (₫)
            </label>
            <input
              id="ac-min"
              type="number"
              min={0}
              step={1000}
              value={form.minOrderAmount}
              onChange={(e) => setForm({ ...form, minOrderAmount: e.target.value })}
              className="form-input"
            />
          </div>

          <div>
            <label className="form-label" htmlFor="ac-usage">
              Tổng số lượt
            </label>
            <input
              id="ac-usage"
              type="number"
              min={1}
              value={form.usageLimit}
              onChange={(e) => setForm({ ...form, usageLimit: e.target.value })}
              placeholder="Bỏ trống = không giới hạn"
              className="form-input"
            />
          </div>

          <div>
            <label className="form-label" htmlFor="ac-peruser">
              Lượt mỗi khách
            </label>
            <input
              id="ac-peruser"
              type="number"
              min={1}
              value={form.perUserLimit}
              onChange={(e) => setForm({ ...form, perUserLimit: e.target.value })}
              placeholder="Bỏ trống = không giới hạn"
              className="form-input"
            />
          </div>

          <div />

          <div>
            <label className="form-label" htmlFor="ac-start">
              Bắt đầu
            </label>
            <input
              id="ac-start"
              type="datetime-local"
              value={form.startsAt}
              onChange={(e) => setForm({ ...form, startsAt: e.target.value })}
              required
              className="form-input"
            />
          </div>

          <div>
            <label className="form-label" htmlFor="ac-end">
              Kết thúc
            </label>
            <input
              id="ac-end"
              type="datetime-local"
              value={form.endsAt}
              onChange={(e) => setForm({ ...form, endsAt: e.target.value })}
              required
              className="form-input"
            />
          </div>

          <div className="sm:col-span-2 flex justify-end gap-2 pt-2">
            <button type="button" onClick={() => setOpen(false)} className="btn btn-secondary">
              Huỷ
            </button>
            <button type="submit" disabled={saving} className="btn btn-primary">
              {saving ? <Loader2 size={15} className="animate-spin" /> : 'Tạo mã'}
            </button>
          </div>
        </form>
      </Modal>
    </div>
  )
}
