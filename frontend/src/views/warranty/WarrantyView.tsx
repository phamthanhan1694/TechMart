'use client'

import { useCallback, useEffect, useState } from 'react'
import Link from 'next/link'
import {
  AlertCircle,
  CheckCircle2,
  Loader2,
  Search,
  ShieldCheck,
  ShieldX,
  Wrench,
} from 'lucide-react'
import * as warrantyApi from '@/lib/api/warranty'
import StatusBadge from '@/components/StatusBadge'
import Modal from '@/components/Modal'
import { EmptyState, Loading } from '@/components/Feedback'
import { ApiError } from '@/lib/api/client'
import { useAuth } from '@/contexts/AuthContext'
import { useToast } from '@/contexts/ToastContext'
import { formatDate, formatDateTime } from '@/lib/format'
import type { WarrantyLookupResponse, WarrantyRequestResponse } from '@/types'

export default function WarrantyView() {
  const { user } = useAuth()
  const toast = useToast()

  const [serialNo, setSerialNo] = useState('')
  const [result, setResult] = useState<WarrantyLookupResponse | null>(null)
  const [lookupError, setLookupError] = useState<string | null>(null)
  const [searching, setSearching] = useState(false)

  const [requests, setRequests] = useState<WarrantyRequestResponse[] | null>(null)
  const [loadingRequests, setLoadingRequests] = useState(false)

  const [claimOpen, setClaimOpen] = useState(false)
  const [reason, setReason] = useState('')
  const [submitting, setSubmitting] = useState(false)

  const loadRequests = useCallback(async () => {
    if (!user) return
    setLoadingRequests(true)
    try {
      const page = await warrantyApi.listWarrantyRequests(undefined, 0, 50)
      setRequests(page.content)
    } catch {
      setRequests(null)
    } finally {
      setLoadingRequests(false)
    }
  }, [user])

  useEffect(() => {
    void loadRequests()
  }, [loadRequests])

  const lookup = async (e: React.FormEvent) => {
    e.preventDefault()
    const serial = serialNo.trim()
    if (!serial) return

    setSearching(true)
    setResult(null)
    setLookupError(null)
    try {
      setResult(await warrantyApi.lookupWarranty(serial))
    } catch (err) {
      setLookupError(
        err instanceof ApiError && err.code === 'SERIAL_NOT_FOUND'
          ? 'Không tìm thấy số serial này trong hệ thống. Kiểm tra lại số in trên hộp hoặc trong phần Cài đặt của máy.'
          : err instanceof ApiError
            ? err.message
            : 'Không tra cứu được, vui lòng thử lại',
      )
    } finally {
      setSearching(false)
    }
  }

  const submitClaim = async () => {
    if (!result) return
    setSubmitting(true)
    try {
      await warrantyApi.createWarrantyRequest(result.serialNo, reason.trim())
      setClaimOpen(false)
      setReason('')
      toast.success('Đã gửi yêu cầu bảo hành')
      await loadRequests()
    } catch (err) {
      // Serial của người khác cũng trả 404 — cố ý, để không dò được máy của ai.
      toast.showError(err, 'Không gửi được yêu cầu bảo hành')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="flex flex-col gap-5 max-w-3xl mx-auto w-full">
      <div>
        <h1 className="page-title flex items-center gap-2">
          <ShieldCheck size={20} className="text-emerald-600 dark:text-emerald-400" />
          Tra cứu bảo hành
        </h1>
        <p className="page-desc">
          Nhập số serial hoặc IMEI in trên hộp máy để kiểm tra máy của bạn còn hạn bảo
          hành hay không.
        </p>
      </div>

      <form onSubmit={lookup} className="card-padded">
        <label className="form-label" htmlFor="serial-input">
          Số serial / IMEI
        </label>
        <div className="flex gap-2">
          <input
            id="serial-input"
            value={serialNo}
            onChange={(e) => setSerialNo(e.target.value)}
            placeholder="VD: SN-IP15PM-000123"
            maxLength={64}
            className="form-input font-mono"
          />
          <button type="submit" disabled={searching || !serialNo.trim()} className="btn btn-primary shrink-0">
            {searching ? <Loader2 size={15} className="animate-spin" /> : <Search size={15} />}
            Tra cứu
          </button>
        </div>
        <p className="form-hint">Không cần đăng nhập để tra cứu.</p>
      </form>

      {lookupError && (
        <div className="form-error">
          <AlertCircle size={15} className="shrink-0 mt-0.5" />
          <span>{lookupError}</span>
        </div>
      )}

      {result && (
        <div
          className={`card-padded border-l-4 ${
            result.underWarranty
              ? 'border-l-emerald-500'
              : 'border-l-slate-300 dark:border-l-slate-600'
          }`}
        >
          <div className="flex items-start gap-3">
            <span
              className={`w-10 h-10 shrink-0 rounded-xl flex items-center justify-center ${
                result.underWarranty
                  ? 'bg-emerald-50 text-emerald-600 dark:bg-emerald-900/30 dark:text-emerald-400'
                  : 'bg-slate-100 text-slate-400 dark:bg-slate-700/50 dark:text-slate-500'
              }`}
            >
              {result.underWarranty ? <CheckCircle2 size={20} /> : <ShieldX size={20} />}
            </span>

            <div className="flex-1 min-w-0">
              <p className="font-bold text-slate-800 dark:text-slate-100">{result.productName}</p>
              <p className="text-2xs text-slate-500 dark:text-slate-400">{result.variantName}</p>
              <p className="text-sm font-semibold mt-1.5 text-slate-700 dark:text-slate-200">
                {result.message}
              </p>
            </div>

            <StatusBadge kind="serial" status={result.status} />
          </div>

          <dl className="grid gap-x-4 gap-y-2 sm:grid-cols-2 mt-4 pt-4 border-t border-slate-100 dark:border-slate-700 text-sm">
            <div>
              <dt className="text-2xs text-slate-500 dark:text-slate-400">Serial</dt>
              <dd className="font-mono font-semibold text-slate-800 dark:text-slate-100">
                {result.serialNo}
              </dd>
            </div>
            <div>
              <dt className="text-2xs text-slate-500 dark:text-slate-400">Thời hạn bảo hành</dt>
              <dd className="font-semibold text-slate-800 dark:text-slate-100">
                {result.warrantyMonths} tháng
              </dd>
            </div>
            <div>
              <dt className="text-2xs text-slate-500 dark:text-slate-400">Bắt đầu bảo hành</dt>
              <dd className="font-semibold text-slate-800 dark:text-slate-100">
                {result.activatedAt ? formatDate(result.activatedAt) : 'Chưa bán ra'}
              </dd>
            </div>
            <div>
              <dt className="text-2xs text-slate-500 dark:text-slate-400">Hết hạn</dt>
              <dd className="font-semibold text-slate-800 dark:text-slate-100">
                {result.expiresAt ? formatDate(result.expiresAt) : '—'}
              </dd>
            </div>
          </dl>

          {result.underWarranty && (
            <div className="mt-4 pt-4 border-t border-slate-100 dark:border-slate-700">
              {user ? (
                <button onClick={() => setClaimOpen(true)} className="btn btn-primary">
                  <Wrench size={15} /> Gửi yêu cầu bảo hành
                </button>
              ) : (
                <p className="text-sm text-slate-500 dark:text-slate-400">
                  <Link
                    href={`/login?next=${encodeURIComponent('/warranty')}`}
                    className="text-primary-600 dark:text-primary-400 font-semibold hover:underline underline-offset-2"
                  >
                    Đăng nhập
                  </Link>{' '}
                  bằng tài khoản đã mua máy này để gửi yêu cầu bảo hành.
                </p>
              )}
            </div>
          )}
        </div>
      )}

      {/* ── Yêu cầu bảo hành của tôi ─────────────────────────────── */}
      {user && (
        <section className="card-padded">
          <h2 className="text-base font-bold text-slate-800 dark:text-slate-100 mb-3">
            Yêu cầu bảo hành của tôi
          </h2>

          {loadingRequests ? (
            <Loading />
          ) : !requests || requests.length === 0 ? (
            <EmptyState
              icon={Wrench}
              title="Bạn chưa gửi yêu cầu bảo hành nào"
              desc="Tra cứu serial của máy bạn đã mua rồi gửi yêu cầu từ kết quả tra cứu."
            />
          ) : (
            <ul className="flex flex-col divide-y divide-slate-100 dark:divide-slate-700">
              {requests.map((r) => (
                <li key={r.id} className="py-3 first:pt-0">
                  <div className="flex items-center gap-2 flex-wrap">
                    <span className="font-semibold text-sm text-slate-800 dark:text-slate-100">
                      {r.productName}
                    </span>
                    <StatusBadge kind="warranty" status={r.status} />
                    <span className="text-2xs text-slate-400 dark:text-slate-500 ml-auto">
                      {formatDateTime(r.createdAt)}
                    </span>
                  </div>
                  <p className="text-2xs font-mono text-slate-500 dark:text-slate-400 mt-0.5">
                    {r.serialNo}
                  </p>
                  <p className="text-sm text-slate-600 dark:text-slate-300 mt-1">{r.reason}</p>
                  {r.resolution && (
                    <p className="text-2xs text-slate-500 dark:text-slate-400 mt-1.5 px-2.5 py-1.5 rounded-md bg-slate-50 dark:bg-surface-card">
                      Phản hồi: {r.resolution}
                    </p>
                  )}
                </li>
              ))}
            </ul>
          )}
        </section>
      )}

      <Modal
        open={claimOpen}
        title="Gửi yêu cầu bảo hành"
        onClose={() => setClaimOpen(false)}
        footer={
          <>
            <button onClick={() => setClaimOpen(false)} className="btn btn-secondary">
              Huỷ
            </button>
            <button
              onClick={() => void submitClaim()}
              disabled={submitting || reason.trim().length === 0}
              className="btn btn-primary"
            >
              {submitting ? <Loader2 size={15} className="animate-spin" /> : 'Gửi yêu cầu'}
            </button>
          </>
        }
      >
        {result && (
          <>
            <p className="text-sm text-slate-600 dark:text-slate-300 mb-3">
              Máy <span className="font-semibold">{result.productName}</span> · serial{' '}
              <span className="font-mono">{result.serialNo}</span>
            </p>
            <label className="form-label" htmlFor="claim-reason">
              Mô tả lỗi
            </label>
            <textarea
              id="claim-reason"
              value={reason}
              onChange={(e) => setReason(e.target.value)}
              maxLength={1000}
              rows={4}
              required
              placeholder="Máy sạc không vào pin, màn hình có điểm chết ở góc trên bên trái…"
              className="form-input resize-none"
            />
            <p className="form-hint">
              Mỗi máy chỉ có một yêu cầu bảo hành đang xử lý tại một thời điểm.
            </p>
          </>
        )}
      </Modal>
    </div>
  )
}
