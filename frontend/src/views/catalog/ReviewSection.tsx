'use client'

import { useEffect, useState } from 'react'
import { MessageSquare, ShieldCheck } from 'lucide-react'
import * as reviewApi from '@/lib/api/review'
import StarRating from '@/components/StarRating'
import Pagination from '@/components/Pagination'
import { EmptyState, Loading } from '@/components/Feedback'
import { formatRelative } from '@/lib/format'
import type { PageResponse, RatingSummary, ReviewResponse } from '@/types'

const PAGE_SIZE = 5

/** Đánh giá của một sản phẩm: điểm trung bình, phân bố sao, và danh sách. */
export default function ReviewSection({ productId }: { productId: number }) {
  const [summary, setSummary] = useState<RatingSummary | null>(null)
  const [page, setPage] = useState(0)
  const [data, setData] = useState<PageResponse<ReviewResponse> | null>(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    reviewApi.ratingSummary(productId).then(setSummary).catch(() => setSummary(null))
  }, [productId])

  useEffect(() => {
    setLoading(true)
    reviewApi
      .reviewsOfProduct(productId, page, PAGE_SIZE)
      .then(setData)
      .catch(() => setData(null))
      .finally(() => setLoading(false))
  }, [productId, page])

  const total = summary?.total ?? 0

  return (
    <section className="card-padded">
      <h2 className="text-base font-bold text-slate-800 dark:text-slate-100 mb-4">
        Đánh giá từ người đã mua
      </h2>

      {total > 0 && summary ? (
        <div className="flex flex-col sm:flex-row gap-6 pb-5 mb-5 border-b border-slate-100 dark:border-slate-700">
          <div className="text-center shrink-0 sm:w-32">
            <p className="text-3xl font-extrabold text-slate-800 dark:text-slate-100 tabular-nums">
              {summary.average.toFixed(1).replace('.', ',')}
            </p>
            <StarRating value={summary.average} size={15} className="justify-center mt-1" />
            <p className="text-2xs text-slate-500 dark:text-slate-400 mt-1">
              {summary.total} đánh giá
            </p>
          </div>

          <div className="flex-1 flex flex-col gap-1.5 justify-center">
            {[5, 4, 3, 2, 1].map((star) => {
              const count = summary.distribution?.[String(star)] ?? 0
              const percent = total > 0 ? (count / total) * 100 : 0
              return (
                <div key={star} className="flex items-center gap-2">
                  <span className="text-2xs text-slate-500 dark:text-slate-400 w-6 tabular-nums">
                    {star}★
                  </span>
                  <div className="flex-1 h-1.5 rounded-full bg-slate-200 dark:bg-slate-700 overflow-hidden">
                    <div className="h-full bg-amber-400" style={{ width: `${percent}%` }} />
                  </div>
                  <span className="text-2xs text-slate-500 dark:text-slate-400 w-8 text-right tabular-nums">
                    {count}
                  </span>
                </div>
              )
            })}
          </div>
        </div>
      ) : null}

      {loading ? (
        <Loading />
      ) : !data || data.content.length === 0 ? (
        <EmptyState
          icon={MessageSquare}
          title="Chưa có đánh giá nào"
          desc="Chỉ khách đã mua và đã nhận hàng mới viết được đánh giá cho sản phẩm này."
        />
      ) : (
        <>
          <ul className="flex flex-col divide-y divide-slate-100 dark:divide-slate-700">
            {data.content.map((r) => (
              <li key={r.id} className="py-3.5 first:pt-0">
                <div className="flex items-center gap-2 flex-wrap">
                  <span className="font-semibold text-sm text-slate-800 dark:text-slate-100">
                    {r.reviewerName}
                  </span>
                  {r.verifiedPurchase && (
                    <span className="badge badge-emerald">
                      <ShieldCheck size={10} /> Đã mua hàng
                    </span>
                  )}
                  <span className="text-2xs text-slate-400 dark:text-slate-500 ml-auto">
                    {formatRelative(r.createdAt)}
                  </span>
                </div>
                <StarRating value={r.rating} className="mt-1" />
                {r.comment && (
                  <p className="text-sm text-slate-600 dark:text-slate-300 mt-1.5 leading-relaxed whitespace-pre-line">
                    {r.comment}
                  </p>
                )}
              </li>
            ))}
          </ul>

          <Pagination
            page={data.page}
            totalPages={data.totalPages}
            totalElements={data.totalElements}
            onChange={setPage}
          />
        </>
      )}
    </section>
  )
}
