'use client'

import { useCallback, useEffect, useState } from 'react'
import { Loader2, RefreshCw, Wrench } from 'lucide-react'
import * as warrantyApi from '@/lib/api/warranty'
import StatusBadge from '@/components/StatusBadge'
import Pagination from '@/components/Pagination'
import Modal from '@/components/Modal'
import { EmptyState, ErrorBox, Loading } from '@/components/Feedback'
import { ApiError } from '@/lib/api/client'
import { useToast } from '@/contexts/ToastContext'
import { formatDateTime } from '@/lib/format'
import type { PageResponse, WarrantyRequestResponse, WarrantyStatus } from '@/types'

const STATUS_LABEL: Record<WarrantyStatus, string> = {
  REQUESTED: 'Chờ xử lý',
  APPROVED: 'Đã duyệt',
  REJECTED: 'Từ chối',
  COMPLETED: 'Hoàn tất',
}

/** Chuyển trạng thái hợp lệ, khớp với máy trạng thái ở WarrantyService. */
const NEXT: Record<WarrantyStatus, WarrantyStatus[]> = {
  REQUESTED: ['APPROVED', 'REJECTED'],
  APPROVED: ['COMPLETED'],
  REJECTED: [],
  COMPLETED: [],
}

export default function AdminWarrantyView() {
  const toast = useToast()

  const [data, setData] = useState<PageResponse<WarrantyRequestResponse> | null>(null)
  const [status, setStatus] = useState<WarrantyStatus | ''>('REQUESTED')
  const [page, setPage] = useState(0)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<{ message: string; code?: string } | null>(null)

  const [target, setTarget] = useState<WarrantyRequestResponse | null>(null)
  const [toStatus, setToStatus] = useState<WarrantyStatus | ''>('')
  const [resolution, setResolution] = useState('')
  const [saving, setSaving] = useState(false)

  const load = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      setData(await warrantyApi.listWarrantyRequests(status || undefined, page, 20))
    } catch (err) {
      setError(
        err instanceof ApiError
          ? { message: err.message, code: err.code }
          : { message: 'Không tải được danh sách yêu cầu bảo hành' },
      )
    } finally {
      setLoading(false)
    }
  }, [status, page])

  useEffect(() => {
    void load()
  }, [load])

  const openResolve = (req: WarrantyRequestResponse) => {
    setTarget(req)
    setToStatus('')
    setResolution('')
  }

  const resolve = async () => {
    if (!target || !toStatus) return
    setSaving(true)
    try {
      await warrantyApi.resolveWarrantyRequest(target.id, toStatus, resolution.trim() || undefined)
      setTarget(null)
      toast.success(`Đã chuyển yêu cầu sang ${STATUS_LABEL[toStatus]}`)
      await load()
    } catch (err) {
      toast.showError(err, 'Không xử lý được yêu cầu')
    } finally {
      setSaving(false)
    }
  }

  return (
    <div className="flex flex-col gap-4">
      <div className="flex items-end justify-between gap-3 flex-wrap">
        <div>
          <h1 className="page-title">Yêu cầu bảo hành</h1>
          <p className="page-desc">
            Duyệt yêu cầu đồng nghĩa xác nhận máy có lỗi thật — serial sẽ được đánh dấu lỗi để
            không quay lại kho bán tiếp.
          </p>
        </div>
        <button onClick={() => void load()} className="btn btn-secondary btn-sm">
          <RefreshCw size={13} /> Làm mới
        </button>
      </div>

      <div className="flex flex-wrap gap-1.5">
        <button
          onClick={() => {
            setStatus('')
            setPage(0)
          }}
          className={`btn btn-sm ${status === '' ? 'btn-primary' : 'btn-secondary'}`}
        >
          Tất cả
        </button>
        {(Object.keys(STATUS_LABEL) as WarrantyStatus[]).map((s) => (
          <button
            key={s}
            onClick={() => {
              setStatus(s)
              setPage(0)
            }}
            className={`btn btn-sm ${status === s ? 'btn-primary' : 'btn-secondary'}`}
          >
            {STATUS_LABEL[s]}
          </button>
        ))}
      </div>

      {error && <ErrorBox message={error.message} code={error.code} />}

      <div className="card">
        {loading ? (
          <Loading />
        ) : !data || data.content.length === 0 ? (
          <EmptyState icon={Wrench} title="Không có yêu cầu nào khớp bộ lọc" />
        ) : (
          <>
            <div className="overflow-x-auto">
              <table className="data-table">
                <thead>
                  <tr>
                    <th>Serial</th>
                    <th>Sản phẩm</th>
                    <th>Lý do</th>
                    <th>Trạng thái</th>
                    <th>Gửi lúc</th>
                    <th />
                  </tr>
                </thead>
                <tbody>
                  {data.content.map((r) => (
                    <tr key={r.id}>
                      <td className="font-mono text-2xs">{r.serialNo}</td>
                      <td className="font-medium text-slate-800 dark:text-slate-100">
                        {r.productName}
                      </td>
                      <td className="max-w-64">
                        <p className="text-slate-600 dark:text-slate-300 clamp-2">{r.reason}</p>
                      </td>
                      <td>
                        <StatusBadge kind="warranty" status={r.status} />
                      </td>
                      <td className="text-2xs text-slate-500 dark:text-slate-400 whitespace-nowrap">
                        {formatDateTime(r.createdAt)}
                      </td>
                      <td className="text-right">
                        <button
                          onClick={() => openResolve(r)}
                          disabled={NEXT[r.status].length === 0}
                          className="btn btn-secondary btn-sm"
                        >
                          {NEXT[r.status].length === 0 ? 'Đã đóng' : 'Xử lý'}
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

      <Modal
        open={target !== null}
        title="Xử lý yêu cầu bảo hành"
        onClose={() => setTarget(null)}
        footer={
          <>
            <button onClick={() => setTarget(null)} className="btn btn-secondary">
              Đóng
            </button>
            <button
              onClick={() => void resolve()}
              disabled={!toStatus || saving}
              className="btn btn-primary"
            >
              {saving ? <Loader2 size={15} className="animate-spin" /> : 'Xác nhận'}
            </button>
          </>
        }
      >
        {target && (
          <div className="flex flex-col gap-3">
            <div>
              <p className="font-semibold text-slate-800 dark:text-slate-100">
                {target.productName}
              </p>
              <p className="text-2xs font-mono text-slate-500 dark:text-slate-400">
                {target.serialNo}
              </p>
            </div>

            <div>
              <p className="section-label mb-1">Lý do khách báo</p>
              <p className="text-sm text-slate-600 dark:text-slate-300 whitespace-pre-line">
                {target.reason}
              </p>
            </div>

            <div>
              <p className="section-label mb-1.5">Chuyển sang</p>
              <div className="flex flex-wrap gap-1.5">
                {NEXT[target.status].map((s) => (
                  <button
                    key={s}
                    onClick={() => setToStatus(s)}
                    className={`btn btn-sm ${toStatus === s ? 'btn-primary' : 'btn-secondary'}`}
                  >
                    {STATUS_LABEL[s]}
                  </button>
                ))}
              </div>
            </div>

            <div>
              <label className="form-label" htmlFor="aw-resolution">
                Phản hồi cho khách
              </label>
              <textarea
                id="aw-resolution"
                value={resolution}
                onChange={(e) => setResolution(e.target.value)}
                maxLength={1000}
                rows={3}
                placeholder="Đã thay pin mới, máy hoạt động bình thường…"
                className="form-input resize-none"
              />
            </div>
          </div>
        )}
      </Modal>
    </div>
  )
}
