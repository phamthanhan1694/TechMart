'use client'

import { useCallback, useEffect, useRef, useState } from 'react'
import { useSearchParams } from 'next/navigation'
import { Loader2, Lock, LockOpen, Plus, Search, TimerReset, Users } from 'lucide-react'
import * as usersApi from '@/lib/api/adminUsers'
import Modal from '@/components/Modal'
import Pagination from '@/components/Pagination'
import StatusBadge from '@/components/StatusBadge'
import { EmptyState, ErrorBox, Loading } from '@/components/Feedback'
import { ApiError } from '@/lib/api/client'
import { useAuth } from '@/contexts/AuthContext'
import { useToast } from '@/contexts/ToastContext'
import { formatDate, formatDateTime, formatMoneyCompact, formatNumber, formatPrice } from '@/lib/format'
import type { AdminUserDetail, AdminUserRow, PageResponse, Role, UserStatus } from '@/types'

const ROLES: Role[] = ['CUSTOMER', 'STAFF', 'ADMIN']
const ROLE_LABEL: Record<Role, string> = { CUSTOMER: 'Khách hàng', STAFF: 'Nhân viên', ADMIN: 'Quản trị' }

const SORTS = [
  { value: 'createdAt,desc', label: 'Mới đăng ký' },
  { value: 'totalSpent,desc', label: 'Chi tiêu nhiều nhất' },
  { value: 'paidOrders,desc', label: 'Nhiều đơn nhất' },
  { value: 'lastOrderAt,desc', label: 'Mua gần đây' },
] as const

const EMPTY_FORM = { email: '', password: '', fullName: '', phone: '', role: 'STAFF' as Role }

export default function AdminUsersView() {
  const toast = useToast()
  const { user: me } = useAuth()
  const searchParams = useSearchParams()

  const [data, setData] = useState<PageResponse<AdminUserRow> | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<{ message: string; code?: string } | null>(null)

  const [keywordInput, setKeywordInput] = useState('')
  const [keyword, setKeyword] = useState('')
  const [role, setRole] = useState<Role | ''>('')
  const [status, setStatus] = useState<UserStatus | ''>('')
  const [sort, setSort] = useState<string>(SORTS[0].value)
  const [page, setPage] = useState(0)

  const [detail, setDetail] = useState<AdminUserDetail | null>(null)
  const [detailOpen, setDetailOpen] = useState(false)
  const [busy, setBusy] = useState(false)
  const [reason, setReason] = useState('')

  const [createOpen, setCreateOpen] = useState(false)
  const [form, setForm] = useState(EMPTY_FORM)

  const load = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      setData(
        await usersApi.search({
          keyword: keyword || undefined,
          role: role || undefined,
          status: status || undefined,
          sort,
          page,
          size: 20,
        }),
      )
    } catch (err) {
      setError(
        err instanceof ApiError ? { message: err.message, code: err.code } : { message: 'Không tải được danh sách' },
      )
    } finally {
      setLoading(false)
    }
  }, [keyword, role, status, sort, page])

  useEffect(() => {
    void load()
  }, [load])

  const openDetail = useCallback(async (id: number) => {
    setDetailOpen(true)
    setDetail(null)
    setReason('')
    try {
      setDetail(await usersApi.detail(id))
    } catch (err) {
      setDetailOpen(false)
      toast.showError(err, 'Không mở được hồ sơ')
    }
  }, [toast])

  // Liên kết từ trang thống kê: /admin/users?focus=123 mở thẳng hồ sơ khách đó.
  // Nhớ id đã mở: openDetail đổi danh tính mỗi khi context toast dựng lại, không chặn
  // thì hộp thoại tự bật lại mỗi lần có thông báo.
  const handledFocus = useRef<number | null>(null)
  useEffect(() => {
    const focus = Number(searchParams.get('focus'))
    if (focus > 0 && handledFocus.current !== focus) {
      handledFocus.current = focus
      void openDetail(focus)
    }
  }, [searchParams, openDetail])

  const act = async (fn: () => Promise<unknown>, ok: string) => {
    if (!detail) return
    setBusy(true)
    try {
      await fn()
      toast.success(ok)
      setDetail(await usersApi.detail(detail.user.id))
      await load()
    } catch (err) {
      toast.showError(err)
    } finally {
      setBusy(false)
    }
  }

  const create = async (e: React.FormEvent) => {
    e.preventDefault()
    setBusy(true)
    try {
      await usersApi.create({ ...form, phone: form.phone.trim() || undefined })
      toast.success(`Đã tạo tài khoản ${form.email}`)
      setCreateOpen(false)
      setForm(EMPTY_FORM)
      await load()
    } catch (err) {
      toast.showError(err, 'Không tạo được tài khoản')
    } finally {
      setBusy(false)
    }
  }

  const isSelf = detail != null && me?.id === detail.user.id

  return (
    <div className="flex flex-col gap-4">
      <div className="flex items-end justify-between gap-3 flex-wrap">
        <div>
          <h1 className="page-title">Người dùng</h1>
          <p className="page-desc">
            Khoá tài khoản sẽ thu hồi mọi phiên đăng nhập. Mọi thao tác ở đây được ghi vào nhật ký.
          </p>
        </div>
        <button onClick={() => setCreateOpen(true)} className="btn btn-primary btn-sm">
          <Plus size={14} /> Tạo tài khoản nhân viên
        </button>
      </div>

      <form
        onSubmit={(e) => {
          e.preventDefault()
          setPage(0)
          setKeyword(keywordInput.trim())
        }}
        className="card-padded flex flex-wrap items-end gap-3"
      >
        <div className="flex-1 min-w-48">
          <label className="form-label" htmlFor="u-kw">Tìm kiếm</label>
          <div className="relative">
            <Search size={14} className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400 pointer-events-none" />
            <input
              id="u-kw"
              value={keywordInput}
              onChange={(e) => setKeywordInput(e.target.value)}
              placeholder="Email, họ tên hoặc số điện thoại"
              className="form-input pl-8 py-1.5 text-sm"
            />
          </div>
        </div>
        <div>
          <label className="form-label" htmlFor="u-role">Vai trò</label>
          <select id="u-role" value={role} onChange={(e) => { setPage(0); setRole(e.target.value as Role | '') }} className="form-select py-1.5 text-2xs w-auto">
            <option value="">Tất cả</option>
            {ROLES.map((r) => <option key={r} value={r}>{ROLE_LABEL[r]}</option>)}
          </select>
        </div>
        <div>
          <label className="form-label" htmlFor="u-status">Trạng thái</label>
          <select id="u-status" value={status} onChange={(e) => { setPage(0); setStatus(e.target.value as UserStatus | '') }} className="form-select py-1.5 text-2xs w-auto">
            <option value="">Tất cả</option>
            <option value="ACTIVE">Đang hoạt động</option>
            <option value="LOCKED">Đã khoá</option>
          </select>
        </div>
        <div>
          <label className="form-label" htmlFor="u-sort">Sắp xếp</label>
          <select id="u-sort" value={sort} onChange={(e) => { setPage(0); setSort(e.target.value) }} className="form-select py-1.5 text-2xs w-auto">
            {SORTS.map((s) => <option key={s.value} value={s.value}>{s.label}</option>)}
          </select>
        </div>
        <button type="submit" className="btn btn-secondary btn-sm">Tìm</button>
      </form>

      {error && <ErrorBox message={error.message} code={error.code} />}

      <div className="card">
        {loading && !data ? (
          <Loading />
        ) : !data || data.content.length === 0 ? (
          <EmptyState icon={Users} title="Không có người dùng nào khớp bộ lọc" />
        ) : (
          <div className={`overflow-x-auto transition-opacity ${loading ? 'opacity-60' : ''}`}>
            <table className="data-table">
              <thead>
                <tr>
                  <th>Người dùng</th>
                  <th>Vai trò</th>
                  <th className="text-right">Đơn đã trả</th>
                  <th className="text-right">Tổng chi tiêu</th>
                  <th>Mua gần nhất</th>
                  <th>Đăng ký</th>
                  <th>Trạng thái</th>
                </tr>
              </thead>
              <tbody>
                {data.content.map((u) => (
                  <tr key={u.id} className="cursor-pointer" onClick={() => void openDetail(u.id)}>
                    <td>
                      <p className="font-semibold text-slate-800 dark:text-slate-100">{u.fullName}</p>
                      <p className="text-2xs text-slate-500 dark:text-slate-400">{u.email}</p>
                    </td>
                    <td><span className="badge badge-indigo">{ROLE_LABEL[u.role]}</span></td>
                    <td className="text-right tabular-nums">{formatNumber(u.paidOrders)}</td>
                    <td className="text-right tabular-nums font-semibold">{formatMoneyCompact(u.totalSpent)}</td>
                    <td className="text-2xs whitespace-nowrap">{u.lastOrderAt ? formatDate(u.lastOrderAt) : '—'}</td>
                    <td className="text-2xs whitespace-nowrap">{formatDate(u.createdAt)}</td>
                    <td>
                      {u.status === 'LOCKED'
                        ? <span className="badge badge-red"><Lock size={11} /> Đã khoá</span>
                        : <span className="badge badge-emerald">Hoạt động</span>}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
        {data && (
          <div className="px-4">
            <Pagination page={data.page} totalPages={data.totalPages} totalElements={data.totalElements} onChange={setPage} />
          </div>
        )}
      </div>

      {/* ── Hồ sơ người dùng ───────────────────────────────────── */}
      <Modal open={detailOpen} title="Hồ sơ người dùng" onClose={() => setDetailOpen(false)} wide>
        {!detail ? (
          <Loading />
        ) : (
          <div className="flex flex-col gap-4">
            <div className="flex items-start justify-between gap-3 flex-wrap">
              <div>
                <p className="text-base font-bold text-slate-800 dark:text-slate-100">{detail.user.fullName}</p>
                <p className="text-sm text-slate-500 dark:text-slate-400">
                  {detail.user.email}{detail.user.phone ? ` · ${detail.user.phone}` : ''}
                </p>
                <p className="text-2xs text-slate-500 dark:text-slate-400 mt-0.5">
                  Đăng ký {formatDateTime(detail.user.createdAt)}
                </p>
              </div>
              <div className="text-right">
                <p className="text-xl font-extrabold text-slate-900 dark:text-slate-50">{formatPrice(detail.user.totalSpent)}</p>
                <p className="text-2xs text-slate-500 dark:text-slate-400">{formatNumber(detail.user.paidOrders)} đơn đã thu tiền</p>
              </div>
            </div>

            {detail.loginLockedSeconds > 0 && (
              <div className="flex items-center gap-3 p-3 rounded-lg bg-amber-50 border border-amber-200 dark:bg-amber-900/20 dark:border-amber-800">
                <TimerReset size={16} className="text-amber-600 dark:text-amber-400 shrink-0" />
                <p className="text-sm text-amber-800 dark:text-amber-200 flex-1">
                  Đang bị khoá đăng nhập tạm thời do gõ sai mật khẩu nhiều lần — còn khoảng{' '}
                  {Math.ceil(detail.loginLockedSeconds / 60)} phút.
                </p>
                <button
                  disabled={busy}
                  onClick={() => void act(() => usersApi.clearLoginLockout(detail.user.id), 'Đã gỡ khoá đăng nhập')}
                  className="btn btn-secondary btn-sm"
                >
                  Gỡ khoá
                </button>
              </div>
            )}

            {isSelf ? (
              <p className="text-2xs text-slate-500 dark:text-slate-400">
                Đây là tài khoản của bạn — không thể tự khoá hay tự đổi vai trò.
              </p>
            ) : (
              <div className="grid gap-3 sm:grid-cols-2">
                <div>
                  <label className="form-label" htmlFor="u-newrole">Vai trò</label>
                  <select
                    id="u-newrole"
                    value={detail.user.role}
                    disabled={busy}
                    onChange={(e) => {
                      const next = e.target.value as Role
                      if (!window.confirm(`Đổi vai trò thành ${ROLE_LABEL[next]}? Người này sẽ phải đăng nhập lại.`)) return
                      void act(() => usersApi.changeRole(detail.user.id, next), 'Đã đổi vai trò')
                    }}
                    className="form-select"
                  >
                    {ROLES.map((r) => <option key={r} value={r}>{ROLE_LABEL[r]}</option>)}
                  </select>
                </div>
                <div>
                  <label className="form-label" htmlFor="u-reason">
                    {detail.user.status === 'ACTIVE' ? 'Lý do khoá (ghi vào nhật ký)' : 'Mở khoá tài khoản'}
                  </label>
                  <div className="flex gap-2">
                    {detail.user.status === 'ACTIVE' && (
                      <input id="u-reason" value={reason} onChange={(e) => setReason(e.target.value)} maxLength={500} placeholder="Nghi gian lận…" className="form-input" />
                    )}
                    {detail.user.status === 'ACTIVE' ? (
                      <button
                        disabled={busy}
                        onClick={() => void act(() => usersApi.changeStatus(detail.user.id, 'LOCKED', reason || undefined), 'Đã khoá tài khoản')}
                        className="btn btn-danger shrink-0"
                      >
                        {busy ? <Loader2 size={14} className="animate-spin" /> : <Lock size={14} />} Khoá
                      </button>
                    ) : (
                      <button
                        disabled={busy}
                        onClick={() => void act(() => usersApi.changeStatus(detail.user.id, 'ACTIVE'), 'Đã mở khoá')}
                        className="btn btn-primary shrink-0"
                      >
                        <LockOpen size={14} /> Mở khoá
                      </button>
                    )}
                  </div>
                </div>
              </div>
            )}

            <div>
              <p className="section-label mb-2">10 đơn gần nhất</p>
              {detail.recentOrders.length === 0 ? (
                <p className="text-sm text-slate-500 dark:text-slate-400">Chưa có đơn nào.</p>
              ) : (
                <div className="overflow-x-auto">
                  <table className="data-table">
                    <thead>
                      <tr><th>Mã đơn</th><th>Ngày đặt</th><th>Trạng thái</th><th className="text-right">Tổng</th></tr>
                    </thead>
                    <tbody>
                      {detail.recentOrders.map((o) => (
                        <tr key={o.code}>
                          <td className="font-mono text-2xs">{o.code}</td>
                          <td className="text-2xs whitespace-nowrap">{formatDateTime(o.placedAt)}</td>
                          <td><StatusBadge kind="order" status={o.status} /></td>
                          <td className="text-right tabular-nums">{formatPrice(o.totalAmount)}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </div>
          </div>
        )}
      </Modal>

      {/* ── Tạo tài khoản ──────────────────────────────────────── */}
      <Modal
        open={createOpen}
        title="Tạo tài khoản"
        onClose={() => setCreateOpen(false)}
        footer={
          <>
            <button onClick={() => setCreateOpen(false)} className="btn btn-secondary">Huỷ</button>
            <button type="submit" form="create-user" disabled={busy} className="btn btn-primary">
              {busy && <Loader2 size={14} className="animate-spin" />} Tạo
            </button>
          </>
        }
      >
        <form id="create-user" onSubmit={create} className="grid gap-3">
          <div>
            <label className="form-label" htmlFor="cu-email">Email</label>
            <input id="cu-email" type="email" required value={form.email} onChange={(e) => setForm({ ...form, email: e.target.value })} className="form-input" />
          </div>
          <div>
            <label className="form-label" htmlFor="cu-name">Họ tên</label>
            <input id="cu-name" required maxLength={150} value={form.fullName} onChange={(e) => setForm({ ...form, fullName: e.target.value })} className="form-input" />
          </div>
          <div className="grid gap-3 sm:grid-cols-2">
            <div>
              <label className="form-label" htmlFor="cu-phone">Số điện thoại</label>
              <input id="cu-phone" pattern="0\d{9}" title="10 chữ số, bắt đầu bằng 0" value={form.phone} onChange={(e) => setForm({ ...form, phone: e.target.value })} className="form-input" />
            </div>
            <div>
              <label className="form-label" htmlFor="cu-role">Vai trò</label>
              <select id="cu-role" value={form.role} onChange={(e) => setForm({ ...form, role: e.target.value as Role })} className="form-select">
                {ROLES.map((r) => <option key={r} value={r}>{ROLE_LABEL[r]}</option>)}
              </select>
            </div>
          </div>
          <div>
            <label className="form-label" htmlFor="cu-pass">Mật khẩu tạm</label>
            <input id="cu-pass" type="password" required minLength={8} maxLength={72} value={form.password} onChange={(e) => setForm({ ...form, password: e.target.value })} className="form-input" />
            <p className="form-hint">Gửi riêng cho nhân viên và nhắc họ đổi ở trang Tài khoản sau lần đăng nhập đầu.</p>
          </div>
        </form>
      </Modal>
    </div>
  )
}
