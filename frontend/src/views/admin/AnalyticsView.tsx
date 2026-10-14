'use client'

import { useCallback, useEffect, useMemo, useState } from 'react'
import Link from 'next/link'
import {
  AlertOctagon,
  AlertTriangle,
  CheckCircle2,
  Download,
  Loader2,
  Star,
} from 'lucide-react'
import * as analyticsApi from '@/lib/api/analytics'
import KpiTile from '@/components/charts/KpiTile'
import TrendChart, { type TrendPoint } from '@/components/charts/TrendChart'
import BarList from '@/components/charts/BarList'
import HeatmapGrid from '@/components/charts/HeatmapGrid'
import ShareBar from '@/components/charts/ShareBar'
import { ErrorBox, Loading } from '@/components/Feedback'
import { ApiError } from '@/lib/api/client'
import { useToast } from '@/contexts/ToastContext'
import {
  formatDate,
  formatDateTime,
  formatMoneyCompact,
  formatNumber,
  formatPercent,
  formatPrice,
  parseLocalDate,
  toDateInput,
} from '@/lib/format'
import type {
  AnalyticsOverview,
  Breakdown,
  BreakdownDimension,
  CouponStats,
  CustomerStats,
  Funnel,
  Heatmap,
  InventoryStats,
  PaymentMethodStats,
  ReviewStats,
  SeriesPoint,
  SeriesUnit,
  TimeSeries,
} from '@/types'

// ── Cấu hình hiển thị ────────────────────────────────────────────────────

const PRESETS = [
  { days: 7, label: '7 ngày' },
  { days: 30, label: '30 ngày' },
  { days: 90, label: '90 ngày' },
  { days: 365, label: '12 tháng' },
] as const

type Metric = 'revenue' | 'paidOrders' | 'placedOrders' | 'newCustomers'

const METRICS: { key: Metric; label: string; money: boolean }[] = [
  { key: 'revenue', label: 'Doanh thu', money: true },
  { key: 'paidOrders', label: 'Đơn đã thu tiền', money: false },
  { key: 'placedOrders', label: 'Đơn đặt', money: false },
  { key: 'newCustomers', label: 'Khách mới', money: false },
]

const DIMENSIONS: { key: BreakdownDimension; label: string }[] = [
  { key: 'category', label: 'Danh mục' },
  { key: 'brand', label: 'Thương hiệu' },
  { key: 'province', label: 'Tỉnh / thành' },
]

const STAGE_LABEL: Record<string, string> = {
  PLACED: 'Đặt hàng',
  PAID: 'Đã thanh toán',
  PACKED: 'Đã đóng gói',
  SHIPPED: 'Đã giao cho vận chuyển',
  DELIVERED: 'Giao thành công',
}

const METHOD_LABEL: Record<string, string> = { MOCK_CARD: 'Thẻ', COD: 'Tiền mặt khi nhận (COD)' }

/** Màu trạng thái cố định - chỉ dùng khi màu mang nghĩa tốt/xấu, và luôn đi kèm biểu tượng. */
const STATUS_COLOR = { good: '#0ca30c', warning: '#fab219', critical: '#d03b3b' }

const WEEKDAYS = ['Chủ Nhật', 'Thứ Hai', 'Thứ Ba', 'Thứ Tư', 'Thứ Năm', 'Thứ Sáu', 'Thứ Bảy']

function pad(n: number) {
  return String(n).padStart(2, '0')
}

function shortLabel(iso: string, unit: SeriesUnit) {
  const d = parseLocalDate(iso)
  if (unit === 'month') return `${pad(d.getMonth() + 1)}/${d.getFullYear() % 100}`
  return `${pad(d.getDate())}/${pad(d.getMonth() + 1)}`
}

function fullLabel(iso: string, unit: SeriesUnit) {
  const d = parseLocalDate(iso)
  const date = `${pad(d.getDate())}/${pad(d.getMonth() + 1)}/${d.getFullYear()}`
  if (unit === 'month') return `Tháng ${pad(d.getMonth() + 1)}/${d.getFullYear()}`
  if (unit === 'week') return `Tuần từ ${date}`
  return `${WEEKDAYS[d.getDay()]}, ${date}`
}

// ── Khung một khối ────────────────────────────────────────────────────────

function Section({
  title,
  desc,
  actions,
  children,
  className = '',
}: {
  title: string
  desc?: React.ReactNode
  actions?: React.ReactNode
  children: React.ReactNode
  className?: string
}) {
  return (
    <section className={`card-padded min-w-0 ${className}`}>
      <div className="flex items-start justify-between gap-3 flex-wrap mb-4">
        <div className="min-w-0">
          <h2 className="text-base font-bold text-slate-800 dark:text-slate-100">{title}</h2>
          {desc && <p className="text-2xs text-slate-500 dark:text-slate-400 mt-0.5">{desc}</p>}
        </div>
        {actions}
      </div>
      {children}
    </section>
  )
}

function Tabs<T extends string>({
  value,
  options,
  onChange,
  label,
}: {
  value: T
  options: { key: T; label: string }[]
  onChange: (v: T) => void
  label: string
}) {
  return (
    <div role="tablist" aria-label={label} className="flex flex-wrap gap-1">
      {options.map((o) => (
        <button
          key={o.key}
          role="tab"
          aria-selected={value === o.key}
          onClick={() => onChange(o.key)}
          className={`btn btn-sm ${value === o.key ? 'btn-primary' : 'btn-secondary'}`}
        >
          {o.label}
        </button>
      ))}
    </div>
  )
}

// ── Trang ─────────────────────────────────────────────────────────────────

interface Data {
  overview: AnalyticsOverview
  series: TimeSeries
  funnel: Funnel
  heatmap: Heatmap
  customers: CustomerStats
  payments: PaymentMethodStats[]
  coupons: CouponStats
  reviews: ReviewStats
  inventory: InventoryStats
}

export default function AnalyticsView() {
  const toast = useToast()

  // Bộ lọc: MỘT hàng ở trên cùng, áp cho mọi khối bên dưới để số liệu luôn khớp nhau.
  const [days, setDays] = useState<number | null>(30)
  const [custom, setCustom] = useState({ from: '', to: '' })
  const [unit, setUnit] = useState<SeriesUnit>('day')

  const [metric, setMetric] = useState<Metric>('revenue')
  const [dimension, setDimension] = useState<BreakdownDimension>('category')

  const [data, setData] = useState<Data | null>(null)
  const [breakdown, setBreakdown] = useState<Breakdown | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<{ message: string; code?: string } | null>(null)
  const [exporting, setExporting] = useState(false)

  const range = useMemo(() => {
    if (days == null) return { from: custom.from || undefined, to: custom.to || undefined }
    const to = new Date()
    const from = new Date()
    from.setDate(from.getDate() - days + 1)
    return { from: toDateInput(from), to: toDateInput(to) }
  }, [days, custom])

  const load = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const [overview, series, funnel, heatmap, customers, payments, coupons, reviews, inventory] =
        await Promise.all([
          analyticsApi.overview(range),
          analyticsApi.timeseries({ ...range, groupBy: unit }),
          analyticsApi.funnel(range),
          analyticsApi.heatmap(range),
          analyticsApi.customers({ ...range, limit: 8 }),
          analyticsApi.payments(range),
          analyticsApi.coupons(range),
          analyticsApi.reviews(range),
          analyticsApi.inventory({ lowThreshold: 10, slowDays: 30 }),
        ])
      setData({ overview, series, funnel, heatmap, customers, payments, coupons, reviews, inventory })
    } catch (err) {
      setError(
        err instanceof ApiError
          ? { message: err.message, code: err.code }
          : { message: 'Không tải được số liệu thống kê' },
      )
    } finally {
      setLoading(false)
    }
  }, [range, unit])

  useEffect(() => {
    void load()
  }, [load])

  // Cơ cấu có tab riêng nên tải riêng - đổi tab không phải tải lại cả trang.
  useEffect(() => {
    let cancelled = false
    analyticsApi
      .breakdown({ ...range, dimension, limit: 8 })
      .then((b) => !cancelled && setBreakdown(b))
      .catch(() => !cancelled && setBreakdown(null))
    return () => {
      cancelled = true
    }
  }, [range, dimension])

  const exportCsv = async () => {
    setExporting(true)
    try {
      await analyticsApi.exportOrdersCsv(range)
      toast.success('Đã xuất file CSV')
    } catch (err) {
      toast.showError(err, 'Không xuất được file')
    } finally {
      setExporting(false)
    }
  }

  const metricDef = METRICS.find((m) => m.key === metric)!
  const trendPoints: TrendPoint[] = useMemo(() => {
    if (!data) return []
    const s = data.series
    const pick = (p: SeriesPoint | undefined) => (p ? Number(p[metric]) : null)
    return s.points.map((p, i) => ({
      label: shortLabel(p.period, s.groupBy),
      fullLabel: fullLabel(p.period, s.groupBy),
      current: Number(p[metric]),
      previous: pick(s.previous[i]),
      previousLabel: s.previous[i] ? fullLabel(s.previous[i].period, s.groupBy).toLowerCase() : undefined,
    }))
  }, [data, metric])

  if (loading && !data) return <Loading label="Đang tổng hợp số liệu…" />

  const money = (n: number) => formatMoneyCompact(n)
  const count = (n: number) => formatNumber(Number(n))
  const pct = (n: number) => formatPercent(Number(n), 1)

  return (
    <div className="flex flex-col gap-5">
      <div className="flex items-end justify-between gap-3 flex-wrap">
        <div>
          <h1 className="page-title">Thống kê</h1>
          <p className="page-desc">
            Tính theo giờ Việt Nam. Doanh thu chỉ gồm đơn đã thu tiền (PAID → DELIVERED); mọi chỉ số
            được so với kỳ liền trước cùng độ dài.
          </p>
        </div>
        <button onClick={() => void exportCsv()} disabled={exporting} className="btn btn-secondary btn-sm">
          {exporting ? <Loader2 size={13} className="animate-spin" /> : <Download size={13} />}
          Xuất đơn hàng (CSV)
        </button>
      </div>

      {/* ── Bộ lọc ─────────────────────────────────────────────── */}
      <div className="card-padded flex flex-wrap items-end gap-3">
        <div>
          <p className="form-label">Khoảng thời gian</p>
          <div className="flex flex-wrap gap-1">
            {PRESETS.map((p) => (
              <button
                key={p.days}
                onClick={() => {
                  setDays(p.days)
                  // Khoảng dài thì gộp theo tuần/tháng: 365 cột ngày là một dải nhiễu không đọc được
                  setUnit(p.days >= 365 ? 'month' : p.days >= 90 ? 'week' : 'day')
                }}
                className={`btn btn-sm ${days === p.days ? 'btn-primary' : 'btn-secondary'}`}
              >
                {p.label}
              </button>
            ))}
          </div>
        </div>
        <div className="flex items-end gap-2">
          <div>
            <label className="form-label" htmlFor="an-from">Từ ngày</label>
            <input
              id="an-from"
              type="date"
              value={days == null ? custom.from : range.from ?? ''}
              onChange={(e) => {
                setDays(null)
                setCustom({ from: e.target.value, to: days == null ? custom.to : range.to ?? '' })
              }}
              className="form-input py-1.5 text-2xs"
            />
          </div>
          <div>
            <label className="form-label" htmlFor="an-to">Đến ngày</label>
            <input
              id="an-to"
              type="date"
              value={days == null ? custom.to : range.to ?? ''}
              onChange={(e) => {
                setDays(null)
                setCustom({ from: days == null ? custom.from : range.from ?? '', to: e.target.value })
              }}
              className="form-input py-1.5 text-2xs"
            />
          </div>
        </div>
        <div>
          <label className="form-label" htmlFor="an-unit">Gộp theo</label>
          <select
            id="an-unit"
            value={unit}
            onChange={(e) => setUnit(e.target.value as SeriesUnit)}
            className="form-select py-1.5 text-2xs w-auto"
          >
            <option value="day">Ngày</option>
            <option value="week">Tuần</option>
            <option value="month">Tháng</option>
          </select>
        </div>
        {loading && <Loader2 size={16} className="animate-spin text-slate-400 mb-2" aria-label="Đang tải" />}
      </div>

      {error && <ErrorBox message={error.message} code={error.code} />}

      {data && (
        // Tải lại thì giữ khung cũ, chỉ mờ đi - không nhảy bố cục, không nháy khung xương
        <div className={`flex flex-col gap-5 transition-opacity ${loading ? 'opacity-60' : ''}`}>
          <p className="text-2xs text-slate-500 dark:text-slate-400 -mb-2">
            Kỳ này {formatDate(data.overview.range.from)} – {formatDate(data.overview.range.to)} ·
            kỳ trước {formatDate(data.overview.previousRange.from)} – {formatDate(data.overview.previousRange.to)}
          </p>

          {/* ── KPI ─────────────────────────────────────────────── */}
          <section className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
            <KpiTile label="Doanh thu" kpi={data.overview.revenue} format={money} />
            <KpiTile label="Đơn đã thu tiền" kpi={data.overview.paidOrders} format={count} />
            <KpiTile label="Giá trị TB mỗi đơn" kpi={data.overview.averageOrderValue} format={money} />
            <KpiTile label="Sản phẩm bán ra" kpi={data.overview.unitsSold} format={count} />
            <KpiTile
              label="Tỉ lệ huỷ đơn"
              kpi={data.overview.cancelRate}
              format={pct}
              upIsGood={false}
              hint={`${count(data.funnel.cancelled)} đơn huỷ / ${count(data.funnel.stages[0]?.count ?? 0)} đơn đặt`}
            />
            <KpiTile label="Khách hàng mới" kpi={data.overview.newCustomers} format={count} />
            <KpiTile label="Người mua" kpi={data.overview.buyers} format={count} />
            <KpiTile
              label="Khách quay lại"
              kpi={data.overview.returningBuyerRate}
              format={pct}
              hint="% người mua kỳ này đã từng mua trước đó"
            />
          </section>

          {/* ── Xu hướng ────────────────────────────────────────── */}
          <Section
            title={`${metricDef.label} theo ${unit === 'day' ? 'ngày' : unit === 'week' ? 'tuần' : 'tháng'}`}
            desc="Mọi mốc đều hiện, kể cả mốc bằng 0 - không nối thẳng qua ngày trống."
            actions={<Tabs value={metric} options={METRICS} onChange={setMetric} label="Chỉ số" />}
          >
            <TrendChart
              points={trendPoints}
              metricName={metricDef.label}
              formatAxis={metricDef.money ? money : count}
              formatValue={metricDef.money ? (n) => formatPrice(n) : count}
            />
          </Section>

          <div className="grid gap-5 lg:grid-cols-2 items-start">
            {/* ── Cơ cấu ───────────────────────────────────────── */}
            <Section
              title="Cơ cấu doanh số"
              desc={
                breakdown?.measure === 'ORDER_TOTAL'
                  ? 'Doanh thu đơn (gồm phí vận chuyển, đã trừ giảm giá) theo nơi giao.'
                  : 'Doanh số hàng hoá (trước giảm giá và phí vận chuyển). Nhóm nhỏ gộp vào "Khác".'
              }
              actions={<Tabs value={dimension} options={DIMENSIONS} onChange={setDimension} label="Chiều" />}
            >
              {breakdown ? (
                <BarList
                  rows={breakdown.rows.map((r) => ({
                    key: r.key,
                    label: r.label,
                    value: Number(r.revenue),
                    display: `${money(Number(r.revenue))} · ${formatPercent(Number(r.share), 1)}`,
                    sub: `${count(r.orders)} đơn · ${count(r.units)} sản phẩm`,
                    muted: r.key === '__other__',
                  }))}
                />
              ) : (
                <Loading />
              )}
            </Section>

            {/* ── Phễu ──────────────────────────────────────────── */}
            <Section
              title="Phễu đơn hàng"
              desc="Bao nhiêu đơn đặt trong kỳ đã TỪNG tới mỗi bước - đọc từ lịch sử trạng thái."
            >
              <BarList
                max={data.funnel.stages[0]?.count || 1}
                rows={data.funnel.stages.map((s) => ({
                  key: s.stage,
                  label: STAGE_LABEL[s.stage] ?? s.stage,
                  value: s.count,
                  display: `${count(s.count)} · ${s.percentOfPlaced == null ? '—' : formatPercent(Number(s.percentOfPlaced), 1)}`,
                }))}
              />
              <dl className="grid grid-cols-2 sm:grid-cols-3 gap-3 mt-5 pt-4 border-t border-slate-100 dark:border-slate-700 text-2xs">
                <div>
                  <dt className="text-slate-500 dark:text-slate-400">Đã huỷ</dt>
                  <dd className="font-bold text-slate-800 dark:text-slate-100 tabular-nums">{count(data.funnel.cancelled)}</dd>
                </div>
                <div>
                  <dt className="text-slate-500 dark:text-slate-400">Chờ thanh toán</dt>
                  <dd className="font-bold text-slate-800 dark:text-slate-100 tabular-nums">{count(data.funnel.pendingPayment)}</dd>
                </div>
                <div>
                  <dt className="text-slate-500 dark:text-slate-400">Hoàn tiền</dt>
                  <dd className="font-bold text-slate-800 dark:text-slate-100 tabular-nums">{count(data.funnel.refunded)}</dd>
                </div>
                <div>
                  <dt className="text-slate-500 dark:text-slate-400">Trung vị đặt → trả tiền</dt>
                  <dd className="font-bold text-slate-800 dark:text-slate-100 tabular-nums">
                    {data.funnel.medianHoursToPay == null ? '—' : `${data.funnel.medianHoursToPay} giờ`}
                  </dd>
                </div>
                <div>
                  <dt className="text-slate-500 dark:text-slate-400">Trung vị đặt → nhận hàng</dt>
                  <dd className="font-bold text-slate-800 dark:text-slate-100 tabular-nums">
                    {data.funnel.medianHoursToDeliver == null
                      ? '—'
                      : `${(Number(data.funnel.medianHoursToDeliver) / 24).toFixed(1).replace('.', ',')} ngày`}
                  </dd>
                </div>
              </dl>
            </Section>
          </div>

          {/* ── Bản đồ nhiệt ──────────────────────────────────────── */}
          <Section
            title="Khung giờ đặt hàng"
            desc="Số đơn đã thu tiền theo thứ và giờ (giờ Việt Nam) - dùng để xếp ca trực và chọn giờ mở flash sale."
          >
            <HeatmapGrid cells={data.heatmap.cells} formatValue={(n) => formatMoneyCompact(n)} />
          </Section>

          <div className="grid gap-5 lg:grid-cols-2 items-start">
            {/* ── Thanh toán ────────────────────────────────────── */}
            <Section title="Thanh toán" desc="Theo ngày tạo giao dịch. Tỉ lệ thành công tính trên giao dịch đã có kết quả.">
              <ShareBar
                ariaLabel="Tỉ trọng tiền thu theo phương thức thanh toán"
                segments={data.payments.map((p, i) => ({
                  key: p.method,
                  label: METHOD_LABEL[p.method] ?? p.method,
                  value: Number(p.amount),
                  display: money(Number(p.amount)),
                  color: i === 0 ? 'var(--viz-1)' : 'var(--viz-2)',
                }))}
              />
              <div className="overflow-x-auto mt-4">
                <table className="data-table">
                  <thead>
                    <tr>
                      <th>Phương thức</th>
                      <th className="text-right">Lượt thử</th>
                      <th className="text-right">Thất bại</th>
                      <th className="text-right">Thành công</th>
                    </tr>
                  </thead>
                  <tbody>
                    {data.payments.map((p) => (
                      <tr key={p.method}>
                        <td>{METHOD_LABEL[p.method] ?? p.method}</td>
                        <td className="text-right tabular-nums">{count(p.attempts)}</td>
                        <td className="text-right tabular-nums">{count(p.failed)}</td>
                        <td className="text-right tabular-nums font-semibold">
                          {p.successRate == null ? '—' : formatPercent(Number(p.successRate), 1)}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </Section>

            {/* ── Mã giảm giá ───────────────────────────────────── */}
            <Section
              title="Mã giảm giá"
              desc={
                <>
                  {count(data.coupons.ordersWithCoupon)} / {count(data.coupons.paidOrders)} đơn có dùng mã (
                  {data.coupons.couponOrderShare == null ? '—' : formatPercent(Number(data.coupons.couponOrderShare), 1)}) ·
                  tổng giảm {formatPrice(data.coupons.discountTotal)}
                </>
              }
              actions={
                <Link href="/admin/coupons" className="btn btn-secondary btn-sm">
                  Quản lý mã
                </Link>
              }
            >
              {data.coupons.rows.length === 0 ? (
                <p className="text-sm text-slate-500 dark:text-slate-400 text-center py-6">Chưa có đơn nào dùng mã.</p>
              ) : (
                <div className="overflow-x-auto">
                  <table className="data-table">
                    <thead>
                      <tr>
                        <th>Mã</th>
                        <th className="text-right">Đơn</th>
                        <th className="text-right">Đã giảm</th>
                        <th className="text-right">Doanh thu</th>
                      </tr>
                    </thead>
                    <tbody>
                      {data.coupons.rows.map((c) => (
                        <tr key={c.code}>
                          <td className="font-mono font-bold">{c.code}</td>
                          <td className="text-right tabular-nums">{count(c.orders)}</td>
                          <td className="text-right tabular-nums">{money(Number(c.discount))}</td>
                          <td className="text-right tabular-nums font-semibold">{money(Number(c.revenue))}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </Section>
          </div>

          <div className="grid gap-5 lg:grid-cols-2 items-start">
            {/* ── Khách hàng ────────────────────────────────────── */}
            <Section
              title="Khách chi tiêu nhiều nhất"
              desc={`${count(data.customers.newCustomers)} khách mới · ${count(data.customers.returningBuyers)} / ${count(data.customers.buyers)} người mua là khách quay lại`}
              actions={
                <Link href="/admin/users" className="btn btn-secondary btn-sm">
                  Tất cả khách hàng
                </Link>
              }
            >
              {data.customers.topCustomers.length === 0 ? (
                <p className="text-sm text-slate-500 dark:text-slate-400 text-center py-6">Chưa có người mua.</p>
              ) : (
                <div className="overflow-x-auto">
                  <table className="data-table">
                    <thead>
                      <tr>
                        <th>Khách hàng</th>
                        <th className="text-right">Đơn</th>
                        <th className="text-right">Chi tiêu</th>
                      </tr>
                    </thead>
                    <tbody>
                      {data.customers.topCustomers.map((c) => (
                        <tr key={c.userId}>
                          <td className="min-w-0">
                            <Link
                              href={`/admin/users?focus=${c.userId}`}
                              className="font-semibold text-slate-800 dark:text-slate-100 hover:text-primary-600 dark:hover:text-primary-400"
                            >
                              {c.fullName}
                            </Link>
                            <p className="text-2xs text-slate-500 dark:text-slate-400 truncate">{c.email}</p>
                          </td>
                          <td className="text-right tabular-nums">{count(c.orders)}</td>
                          <td className="text-right tabular-nums font-semibold">{money(Number(c.revenue))}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </Section>

            {/* ── Đánh giá ──────────────────────────────────────── */}
            <Section
              title="Đánh giá sản phẩm"
              desc={
                data.reviews.total === 0
                  ? 'Chưa có đánh giá trong kỳ.'
                  : `${count(data.reviews.total)} lượt · trung bình ${String(data.reviews.average).replace('.', ',')} / 5`
              }
            >
              <BarList
                max={Math.max(1, ...Object.values(data.reviews.distribution).map(Number))}
                rows={[5, 4, 3, 2, 1].map((star) => {
                  const n = Number(data.reviews.distribution[String(star)] ?? 0)
                  return {
                    key: String(star),
                    label: `${star} sao`,
                    value: n,
                    display: `${count(n)} · ${data.reviews.total ? formatPercent((n * 100) / data.reviews.total, 0) : '—'}`,
                  }
                })}
              />
              {data.reviews.lowestRated.length > 0 && (
                <div className="mt-5 pt-4 border-t border-slate-100 dark:border-slate-700">
                  <p className="section-label mb-2">Điểm thấp nhất (tối thiểu 3 lượt)</p>
                  <ul className="flex flex-col gap-1.5">
                    {data.reviews.lowestRated.map((p) => (
                      <li key={p.productId} className="flex items-center justify-between gap-3 text-sm">
                        <Link
                          href={`/products/${p.slug}`}
                          className="truncate text-slate-700 dark:text-slate-200 hover:text-primary-600 dark:hover:text-primary-400"
                        >
                          {p.name}
                        </Link>
                        <span className="flex items-center gap-1 text-2xs font-bold tabular-nums shrink-0 text-slate-700 dark:text-slate-200">
                          <Star size={12} className="text-amber-500" aria-hidden />
                          {String(p.average).replace('.', ',')} · {p.reviews} lượt
                        </span>
                      </li>
                    ))}
                  </ul>
                </div>
              )}
            </Section>
          </div>

          {/* ── Tồn kho ─────────────────────────────────────────── */}
          <Section
            title="Sức khoẻ tồn kho"
            desc={
              <>
                Ảnh chụp hiện tại, không theo khoảng ngày. Giá trị tính theo <b>giá bán</b> - hệ thống chưa
                lưu giá vốn nên đây không phải giá trị sổ sách.
              </>
            }
            actions={
              <Link href="/admin/inventory" className="btn btn-secondary btn-sm">
                Điều chỉnh tồn kho
              </Link>
            }
          >
            <div className="grid gap-5 lg:grid-cols-2">
              <div className="flex flex-col gap-4">
                <ShareBar
                  ariaLabel="Số phiên bản theo tình trạng tồn kho"
                  segments={[
                    { key: 'ok', label: 'Ổn định', value: data.inventory.healthy, display: count(data.inventory.healthy), color: STATUS_COLOR.good, icon: CheckCircle2 },
                    { key: 'low', label: `Sắp hết (≤ ${data.inventory.lowThreshold})`, value: data.inventory.lowStock, display: count(data.inventory.lowStock), color: STATUS_COLOR.warning, icon: AlertTriangle },
                    { key: 'out', label: 'Hết hàng', value: data.inventory.outOfStock, display: count(data.inventory.outOfStock), color: STATUS_COLOR.critical, icon: AlertOctagon },
                  ]}
                />
                <dl className="grid grid-cols-3 gap-3 text-2xs">
                  <div>
                    <dt className="text-slate-500 dark:text-slate-400">Tổng tồn</dt>
                    <dd className="text-base font-extrabold text-slate-800 dark:text-slate-100">{count(data.inventory.totalUnits)}</dd>
                  </div>
                  <div>
                    <dt className="text-slate-500 dark:text-slate-400">Đang giữ chỗ</dt>
                    <dd className="text-base font-extrabold text-slate-800 dark:text-slate-100">{count(data.inventory.reservedUnits)}</dd>
                  </div>
                  <div>
                    <dt className="text-slate-500 dark:text-slate-400">Giá trị (giá bán)</dt>
                    <dd className="text-base font-extrabold text-slate-800 dark:text-slate-100">{money(Number(data.inventory.stockValue))}</dd>
                  </div>
                </dl>
                <BarList
                  rows={data.inventory.byCategory.map((c) => ({
                    key: c.label,
                    label: c.label,
                    value: Number(c.value),
                    display: money(Number(c.value)),
                    sub: `${count(c.units)} sản phẩm trong kho`,
                  }))}
                />
              </div>

              <div className="min-w-0">
                <p className="section-label mb-2">
                  Bán chậm - còn hàng, không bán được trong {data.inventory.slowDays} ngày
                </p>
                {data.inventory.slowMovers.length === 0 ? (
                  <p className="text-sm text-slate-500 dark:text-slate-400 py-6 text-center">Không có mặt hàng nào đứng im.</p>
                ) : (
                  <div className="overflow-x-auto">
                    <table className="data-table">
                      <thead>
                        <tr>
                          <th>Phiên bản</th>
                          <th className="text-right">Còn</th>
                          <th className="text-right">Vốn nằm kho</th>
                          <th>Bán lần cuối</th>
                        </tr>
                      </thead>
                      <tbody>
                        {data.inventory.slowMovers.map((v) => (
                          <tr key={v.variantId}>
                            <td>
                              <p className="font-semibold text-slate-800 dark:text-slate-100">{v.productName}</p>
                              <p className="text-2xs text-slate-500 dark:text-slate-400">
                                {v.variantName} · <span className="font-mono">{v.sku}</span>
                              </p>
                            </td>
                            <td className="text-right tabular-nums">{count(v.availableQuantity)}</td>
                            <td className="text-right tabular-nums font-semibold">{money(Number(v.stockValue))}</td>
                            <td className="text-2xs whitespace-nowrap">
                              {v.lastSoldAt ? formatDateTime(v.lastSoldAt) : 'Chưa từng bán'}
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                )}
              </div>
            </div>
          </Section>
        </div>
      )}
    </div>
  )
}
