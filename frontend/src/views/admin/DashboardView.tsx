'use client'

import { useCallback, useEffect, useState } from 'react'
import Link from 'next/link'
import {
  AlertTriangle,
  Banknote,
  Inbox,
  Receipt,
  Timer,
  TrendingUp,
  type LucideIcon,
} from 'lucide-react'
import * as adminApi from '@/lib/api/admin'
import RevenueChart from '@/views/admin/RevenueChart'
import StatusBadge from '@/components/StatusBadge'
import { ErrorBox, Loading } from '@/components/Feedback'
import { ApiError } from '@/lib/api/client'
import { formatNumber, formatPrice, toDateInput } from '@/lib/format'
import type {
  DashboardSummary,
  OrderStatus,
  RevenueReport,
  TopProduct,
} from '@/types'

/** Con số dẫn đầu bảng — một ô một ý, không nhồi hai số vào cùng một ô. */
function StatTile({
  icon: Icon,
  label,
  value,
  hint,
  accent,
}: {
  icon: LucideIcon
  label: string
  value: string
  hint?: string
  accent?: 'warning'
}) {
  return (
    <div className="card-padded flex flex-col gap-1">
      <div className="flex items-center gap-2">
        <span
          className={`w-7 h-7 rounded-lg flex items-center justify-center shrink-0 ${
            accent === 'warning'
              ? 'bg-amber-50 text-amber-600 dark:bg-amber-900/30 dark:text-amber-400'
              : 'bg-primary-50 text-primary-600 dark:bg-primary-900/30 dark:text-primary-400'
          }`}
        >
          <Icon size={15} />
        </span>
        <span className="section-label">{label}</span>
      </div>
      <p className="text-xl font-extrabold text-slate-900 dark:text-slate-50 tabular-nums leading-tight">
        {value}
      </p>
      {hint && <p className="text-2xs text-slate-500 dark:text-slate-400">{hint}</p>}
    </div>
  )
}

const RANGES = [
  { days: 7, label: '7 ngày' },
  { days: 30, label: '30 ngày' },
  { days: 90, label: '90 ngày' },
] as const

export default function DashboardView() {
  const [summary, setSummary] = useState<DashboardSummary | null>(null)
  const [revenue, setRevenue] = useState<RevenueReport | null>(null)
  const [top, setTop] = useState<TopProduct[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<{ message: string; code?: string } | null>(null)

  const [days, setDays] = useState<number>(30)
  const [groupBy, setGroupBy] = useState<'day' | 'month'>('day')

  const load = useCallback(async () => {
    setLoading(true)
    setError(null)

    const to = new Date()
    const from = new Date()
    from.setDate(from.getDate() - days + 1)
    const range = { from: toDateInput(from), to: toDateInput(to) }

    try {
      const [s, r, t] = await Promise.all([
        adminApi.dashboardSummary(),
        adminApi.revenueReport({ groupBy, ...range }),
        adminApi.topProducts({ ...range, limit: 10 }),
      ])
      setSummary(s)
      setRevenue(r)
      setTop(t)
    } catch (err) {
      setError(
        err instanceof ApiError
          ? { message: err.message, code: err.code }
          : { message: 'Không tải được số liệu tổng quan' },
      )
    } finally {
      setLoading(false)
    }
  }, [days, groupBy])

  useEffect(() => {
    void load()
  }, [load])

  if (loading && !summary) return <Loading label="Đang tải số liệu…" />

  return (
    <div className="flex flex-col gap-5">
      <div className="flex items-end justify-between gap-3 flex-wrap">
        <div>
          <h1 className="page-title">Tổng quan</h1>
          <p className="page-desc">
            Doanh thu chỉ tính các đơn đã thu được tiền: PAID, PACKED, SHIPPED, DELIVERED.
          </p>
        </div>
        <Link href="/admin/analytics" className="btn btn-secondary btn-sm">
          <TrendingUp size={13} /> Thống kê chi tiết
        </Link>
      </div>

      {error && <ErrorBox message={error.message} code={error.code} />}

      {summary && (
        <>
          <section className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
            <StatTile
              icon={Banknote}
              label="Doanh thu hôm nay"
              value={formatPrice(summary.revenueToday)}
              hint={`${formatNumber(summary.ordersToday)} đơn`}
            />
            <StatTile
              icon={TrendingUp}
              label="Doanh thu tháng này"
              value={formatPrice(summary.revenueThisMonth)}
              hint={`${formatNumber(summary.ordersThisMonth)} đơn`}
            />
            <StatTile
              icon={Receipt}
              label="Giá trị TB mỗi đơn"
              value={formatPrice(summary.averageOrderValue)}
            />
            <StatTile
              icon={AlertTriangle}
              label="Sắp hết hàng"
              value={formatNumber(summary.lowStockVariants)}
              hint="phiên bản dưới ngưỡng"
              accent={summary.lowStockVariants > 0 ? 'warning' : undefined}
            />
          </section>

          {/* Hai số này nói về sức khoẻ hệ thống, không phải doanh số — tách riêng. */}
          <section className="grid gap-3 sm:grid-cols-2">
            <div className="card-padded flex items-center gap-3">
              <span className="w-8 h-8 rounded-lg bg-slate-100 dark:bg-slate-700/50 text-slate-500 dark:text-slate-400 flex items-center justify-center shrink-0">
                <Inbox size={16} />
              </span>
              <div className="min-w-0">
                <p className="section-label">Outbox chưa xử lý</p>
                <p className="text-sm font-bold text-slate-800 dark:text-slate-100 tabular-nums">
                  {formatNumber(summary.pendingOutboxEvents)} sự kiện
                </p>
              </div>
              {summary.pendingOutboxEvents > 100 && (
                <span className="badge badge-amber ml-auto">Tồn đọng</span>
              )}
            </div>

            <div className="card-padded flex items-center gap-3">
              <span className="w-8 h-8 rounded-lg bg-slate-100 dark:bg-slate-700/50 text-slate-500 dark:text-slate-400 flex items-center justify-center shrink-0">
                <Timer size={16} />
              </span>
              <div className="min-w-0">
                <p className="section-label">Phiên đang giữ hàng</p>
                <p className="text-sm font-bold text-slate-800 dark:text-slate-100 tabular-nums">
                  {formatNumber(summary.activeReservations)} phiên
                </p>
              </div>
            </div>
          </section>
        </>
      )}

      {/* ── Doanh thu theo thời gian ─────────────────────────────── */}
      <section className="card-padded">
        <div className="flex items-center justify-between gap-3 flex-wrap mb-4">
          <div>
            <h2 className="text-base font-bold text-slate-800 dark:text-slate-100">
              Doanh thu theo {groupBy === 'day' ? 'ngày' : 'tháng'}
            </h2>
            {revenue && (
              <p className="text-2xs text-slate-500 dark:text-slate-400 mt-0.5">
                Tổng {formatPrice(revenue.totalRevenue)} ·{' '}
                {formatNumber(revenue.totalOrders)} đơn
              </p>
            )}
          </div>

          <div className="flex items-center gap-1.5">
            {RANGES.map((r) => (
              <button
                key={r.days}
                onClick={() => setDays(r.days)}
                className={`btn btn-sm ${days === r.days ? 'btn-primary' : 'btn-secondary'}`}
              >
                {r.label}
              </button>
            ))}
            <select
              value={groupBy}
              onChange={(e) => setGroupBy(e.target.value as 'day' | 'month')}
              aria-label="Gộp theo"
              className="form-select py-1.5 text-2xs w-auto"
            >
              <option value="day">Theo ngày</option>
              <option value="month">Theo tháng</option>
            </select>
          </div>
        </div>

        {revenue ? (
          <RevenueChart points={revenue.points} groupBy={groupBy} />
        ) : (
          <Loading />
        )}
      </section>

      <div className="grid gap-4 lg:grid-cols-2 items-start">
        {/* ── Đơn theo trạng thái ────────────────────────────────── */}
        {summary && (
          <section className="card-padded">
            <h2 className="text-base font-bold text-slate-800 dark:text-slate-100 mb-3">
              Đơn theo trạng thái
            </h2>
            <ul className="flex flex-col gap-2">
              {(Object.entries(summary.ordersByStatus) as [OrderStatus, number][])
                .sort((a, b) => b[1] - a[1])
                .map(([status, count]) => (
                  <li key={status} className="flex items-center gap-2">
                    <StatusBadge kind="order" status={status} />
                    <span className="ml-auto text-sm font-bold text-slate-800 dark:text-slate-100 tabular-nums">
                      {formatNumber(count)}
                    </span>
                  </li>
                ))}
            </ul>
            <Link
              href="/admin/orders"
              className="btn btn-secondary btn-sm w-full mt-4"
            >
              Xem tất cả đơn hàng
            </Link>
          </section>
        )}

        {/* ── Bán chạy nhất ──────────────────────────────────────── */}
        <section className="card-padded">
          <h2 className="text-base font-bold text-slate-800 dark:text-slate-100 mb-3">
            Bán chạy nhất
          </h2>
          {top.length === 0 ? (
            <p className="text-sm text-slate-500 dark:text-slate-400 py-6 text-center">
              Chưa có dữ liệu bán hàng trong khoảng này.
            </p>
          ) : (
            <div className="overflow-x-auto">
              <table className="data-table">
                <thead>
                  <tr>
                    <th>Sản phẩm</th>
                    <th className="text-right">Đã bán</th>
                    <th className="text-right">Doanh thu</th>
                  </tr>
                </thead>
                <tbody>
                  {top.map((p) => (
                    <tr key={p.variantId}>
                      <td>
                        <p className="font-semibold text-slate-800 dark:text-slate-100">
                          {p.productName}
                        </p>
                        <p className="text-2xs text-slate-500 dark:text-slate-400">
                          {p.variantName} · <span className="font-mono">{p.sku}</span>
                        </p>
                      </td>
                      <td className="text-right tabular-nums font-semibold">
                        {formatNumber(p.quantitySold)}
                      </td>
                      <td className="text-right tabular-nums">{formatPrice(p.revenue)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </section>
      </div>
    </div>
  )
}
