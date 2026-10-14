'use client'

import { Fragment, useCallback, useEffect, useState } from 'react'
import { ChevronDown, ChevronRight, ClipboardList } from 'lucide-react'
import * as engagementApi from '@/lib/api/engagement'
import Pagination from '@/components/Pagination'
import { EmptyState, ErrorBox, Loading } from '@/components/Feedback'
import { ApiError } from '@/lib/api/client'
import { formatDateTime } from '@/lib/format'
import type { AuditAction, AuditLogEntry, PageResponse } from '@/types'

const ACTION_LABEL: Record<AuditAction, string> = {
  ORDER_STATUS_CHANGED: 'Đổi trạng thái đơn',
  PRODUCT_CREATED: 'Tạo sản phẩm',
  PRODUCT_UPDATED: 'Sửa sản phẩm',
  PRODUCT_DELETED: 'Xoá sản phẩm',
  VARIANT_CREATED: 'Thêm phiên bản',
  VARIANT_UPDATED: 'Sửa phiên bản / giá',
  STOCK_ADJUSTED: 'Điều chỉnh tồn kho',
  COUPON_CREATED: 'Tạo mã giảm giá',
  COUPON_DEACTIVATED: 'Tắt mã giảm giá',
  FLASH_SALE_CREATED: 'Tạo flash sale',
  WARRANTY_RESOLVED: 'Xử lý bảo hành',
  USER_CREATED: 'Tạo tài khoản',
  USER_LOCKED: 'Khoá tài khoản',
  USER_UNLOCKED: 'Mở khoá tài khoản',
  USER_ROLE_CHANGED: 'Đổi vai trò',
  LOGIN_LOCKOUT_CLEARED: 'Gỡ khoá đăng nhập',
  ORDERS_EXPORTED: 'Xuất dữ liệu đơn hàng',
}

/** Hành động nhạy cảm tô vàng để lướt mắt là thấy - vẫn có nhãn chữ, màu chỉ là phụ. */
const SENSITIVE = new Set<AuditAction>(['USER_LOCKED', 'USER_ROLE_CHANGED', 'ORDERS_EXPORTED', 'PRODUCT_DELETED', 'STOCK_ADJUSTED'])

/**
 * Nhật ký thao tác quản trị - chỉ đọc. Backend không có endpoint sửa/xoá, và bảng được một
 * trigger ở DB chặn UPDATE/DELETE (ADR-0014).
 */
export default function AuditLogView() {
  const [data, setData] = useState<PageResponse<AuditLogEntry> | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<{ message: string; code?: string } | null>(null)

  const [action, setAction] = useState<AuditAction | ''>('')
  const [targetId, setTargetId] = useState('')
  const [from, setFrom] = useState('')
  const [to, setTo] = useState('')
  const [page, setPage] = useState(0)
  const [expanded, setExpanded] = useState<number | null>(null)

  const load = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      setData(
        await engagementApi.auditLogs({
          action: action || undefined,
          targetId: targetId.trim() || undefined,
          from: from || undefined,
          to: to || undefined,
          page,
          size: 30,
        }),
      )
    } catch (err) {
      setError(err instanceof ApiError ? { message: err.message, code: err.code } : { message: 'Không tải được nhật ký' })
    } finally {
      setLoading(false)
    }
  }, [action, targetId, from, to, page])

  useEffect(() => {
    void load()
  }, [load])

  return (
    <div className="flex flex-col gap-4">
      <div>
        <h1 className="page-title">Nhật ký thao tác</h1>
        <p className="page-desc">
          Ai đã làm gì, lúc nào, đổi từ gì sang gì. Chỉ thêm, không sửa, không xoá được — kể cả bằng SQL.
        </p>
      </div>

      <div className="card-padded flex flex-wrap items-end gap-3">
        <div>
          <label className="form-label" htmlFor="al-action">Hành động</label>
          <select id="al-action" value={action} onChange={(e) => { setPage(0); setAction(e.target.value as AuditAction | '') }} className="form-select py-1.5 text-2xs w-auto">
            <option value="">Tất cả</option>
            {(Object.keys(ACTION_LABEL) as AuditAction[]).map((a) => (
              <option key={a} value={a}>{ACTION_LABEL[a]}</option>
            ))}
          </select>
        </div>
        <div>
          <label className="form-label" htmlFor="al-target">Mã đối tượng</label>
          <input id="al-target" value={targetId} onChange={(e) => { setPage(0); setTargetId(e.target.value) }} placeholder="Mã đơn, id người dùng…" className="form-input py-1.5 text-2xs" />
        </div>
        <div>
          <label className="form-label" htmlFor="al-from">Từ ngày</label>
          <input id="al-from" type="date" value={from} onChange={(e) => { setPage(0); setFrom(e.target.value) }} className="form-input py-1.5 text-2xs" />
        </div>
        <div>
          <label className="form-label" htmlFor="al-to">Đến ngày</label>
          <input id="al-to" type="date" value={to} onChange={(e) => { setPage(0); setTo(e.target.value) }} className="form-input py-1.5 text-2xs" />
        </div>
      </div>

      {error && <ErrorBox message={error.message} code={error.code} />}

      <div className="card">
        {loading && !data ? (
          <Loading />
        ) : !data || data.content.length === 0 ? (
          <EmptyState icon={ClipboardList} title="Chưa có thao tác nào khớp bộ lọc" />
        ) : (
          <div className={`overflow-x-auto transition-opacity ${loading ? 'opacity-60' : ''}`}>
            <table className="data-table">
              <thead>
                <tr>
                  <th className="w-6" />
                  <th>Thời gian</th>
                  <th>Người thực hiện</th>
                  <th>Hành động</th>
                  <th>Đối tượng</th>
                </tr>
              </thead>
              <tbody>
                {data.content.map((log) => {
                  const open = expanded === log.id
                  return (
                    <Fragment key={log.id}>
                      <tr className="cursor-pointer" onClick={() => setExpanded(open ? null : log.id)} aria-expanded={open}>
                        <td className="text-slate-400">{open ? <ChevronDown size={14} /> : <ChevronRight size={14} />}</td>
                        <td className="text-2xs whitespace-nowrap">{formatDateTime(log.createdAt)}</td>
                        <td>
                          <p className="text-sm text-slate-800 dark:text-slate-100">{log.actorEmail}</p>
                          <p className="text-2xs text-slate-500 dark:text-slate-400">{log.actorRole}</p>
                        </td>
                        <td>
                          <span className={`badge ${SENSITIVE.has(log.action) ? 'badge-amber' : 'badge-slate'}`}>
                            {ACTION_LABEL[log.action] ?? log.action}
                          </span>
                        </td>
                        <td className="text-2xs">
                          {log.targetType}
                          {log.targetId && <span className="font-mono"> #{log.targetId}</span>}
                        </td>
                      </tr>
                      {open && (
                        <tr>
                          <td />
                          <td colSpan={4}>
                            <pre className="text-2xs font-mono whitespace-pre-wrap break-all bg-slate-50 dark:bg-slate-800/60 rounded-lg p-3 text-slate-700 dark:text-slate-200">
                              {JSON.stringify(log.detail, null, 2)}
                            </pre>
                          </td>
                        </tr>
                      )}
                    </Fragment>
                  )
                })}
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
    </div>
  )
}
