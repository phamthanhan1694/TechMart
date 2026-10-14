'use client'

import { useEffect, useState } from 'react'
import Link from 'next/link'
import { ChevronRight, Package } from 'lucide-react'
import * as orderApi from '@/lib/api/order'
import StatusBadge from '@/components/StatusBadge'
import Pagination from '@/components/Pagination'
import { EmptyState, ErrorBox, Loading } from '@/components/Feedback'
import { ApiError } from '@/lib/api/client'
import { formatDateTime, formatPrice } from '@/lib/format'
import type { OrderSummaryResponse, PageResponse } from '@/types'

export default function OrderListView() {
  const [page, setPage] = useState(0)
  const [data, setData] = useState<PageResponse<OrderSummaryResponse> | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<{ message: string; code?: string } | null>(null)

  useEffect(() => {
    setLoading(true)
    setError(null)
    orderApi
      .listMyOrders(page, 20)
      .then(setData)
      .catch((err) =>
        setError(
          err instanceof ApiError
            ? { message: err.message, code: err.code }
            : { message: 'Không tải được danh sách đơn hàng' },
        ),
      )
      .finally(() => setLoading(false))
  }, [page])

  return (
    <div className="flex flex-col gap-4">
      <div>
        <h1 className="page-title">Đơn hàng của tôi</h1>
        <p className="page-desc">Theo dõi trạng thái và lịch sử mua hàng</p>
      </div>

      {error && <ErrorBox message={error.message} code={error.code} />}

      {loading ? (
        <Loading />
      ) : !data || data.content.length === 0 ? (
        <div className="card">
          <EmptyState
            icon={Package}
            title="Chưa có đơn hàng nào"
            desc="Đơn hàng bạn đặt sẽ xuất hiện ở đây."
            action={
              <Link href="/products" className="btn btn-primary">
                Bắt đầu mua sắm
              </Link>
            }
          />
        </div>
      ) : (
        <>
          <ul className="flex flex-col gap-2.5">
            {data.content.map((order) => (
              <li key={order.id}>
                <Link
                  href={`/orders/${order.code}`}
                  className="card p-4 flex items-center gap-3 hover:shadow-md transition-shadow"
                >
                  <div className="flex-1 min-w-0">
                    <div className="flex items-center gap-2 flex-wrap">
                      <span className="font-mono font-bold text-sm text-slate-800 dark:text-slate-100">
                        {order.code}
                      </span>
                      <StatusBadge kind="order" status={order.status} />
                    </div>
                    <p className="text-2xs text-slate-500 dark:text-slate-400 mt-1">
                      {formatDateTime(order.placedAt)} · {order.totalItems} sản phẩm
                    </p>
                  </div>

                  <div className="text-right shrink-0">
                    <p className="price text-sm">{formatPrice(order.totalAmount)}</p>
                  </div>

                  <ChevronRight size={16} className="text-slate-400 shrink-0" />
                </Link>
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
    </div>
  )
}
