'use client'

import { useEffect, useState } from 'react'
import Link from 'next/link'
import { useSearchParams } from 'next/navigation'
import { GitCompareArrows } from 'lucide-react'
import * as catalogApi from '@/lib/api/catalog'
import ProductImage from '@/components/ProductImage'
import { EmptyState, ErrorBox, Loading } from '@/components/Feedback'
import { ApiError } from '@/lib/api/client'
import { formatPrice, specLabel, specValue } from '@/lib/format'
import type { CompareResponse } from '@/types'

export default function CompareView() {
  const searchParams = useSearchParams()
  const idsParam = searchParams.get('ids') ?? ''

  const [data, setData] = useState<CompareResponse | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<{ message: string; code?: string } | null>(null)

  useEffect(() => {
    const ids = idsParam
      .split(',')
      .map((s) => Number(s.trim()))
      .filter((n) => Number.isFinite(n) && n > 0)

    if (ids.length < 2) {
      setData(null)
      setLoading(false)
      return
    }

    setLoading(true)
    setError(null)
    catalogApi
      .compareProducts(ids)
      .then(setData)
      .catch((err) =>
        setError(
          err instanceof ApiError
            ? { message: err.message, code: err.code }
            : { message: 'Không so sánh được' },
        ),
      )
      .finally(() => setLoading(false))
  }, [idsParam])

  if (loading) return <Loading label="Đang tải so sánh…" />

  if (error) return <ErrorBox message={error.message} code={error.code} />

  if (!data || data.products.length < 2) {
    return (
      <div className="card">
        <EmptyState
          icon={GitCompareArrows}
          title="Chọn ít nhất 2 sản phẩm để so sánh"
          desc="Ở trang danh sách, di chuột lên thẻ sản phẩm và bấm biểu tượng so sánh. Tối đa 3 sản phẩm."
          action={
            <Link href="/products" className="btn btn-primary">
              Chọn sản phẩm
            </Link>
          }
        />
      </div>
    )
  }

  return (
    <div className="flex flex-col gap-4">
      <div>
        <h1 className="page-title">So sánh sản phẩm</h1>
        <p className="page-desc">
          Bảng gộp mọi thông số xuất hiện ở các sản phẩm đang so sánh; ô trống nghĩa là
          sản phẩm đó không công bố thông số này.
        </p>
      </div>

      <div className="card overflow-x-auto">
        <table className="data-table min-w-[40rem]">
          <thead>
            <tr>
              <th className="w-40">Thông số</th>
              {data.products.map((p) => (
                <th key={p.id} className="min-w-48">
                  <Link href={`/products/${p.slug}`} className="block group">
                    <ProductImage
                      src={p.thumbnailUrl}
                      alt={p.name}
                      sizes="140px"
                      className="w-full max-w-32 mb-2"
                    />
                    <span className="block normal-case tracking-normal text-sm font-bold text-slate-800 dark:text-slate-100 group-hover:text-primary-600 dark:group-hover:text-primary-400 clamp-2">
                      {p.name}
                    </span>
                    <span className="block normal-case tracking-normal price text-sm mt-1">
                      {formatPrice(p.minPrice)}
                    </span>
                  </Link>
                </th>
              ))}
            </tr>
          </thead>

          <tbody>
            <tr>
              <td className="text-slate-500 dark:text-slate-400">Thương hiệu</td>
              {data.products.map((p) => (
                <td key={p.id} className="font-medium text-slate-800 dark:text-slate-100">
                  {p.brandName}
                </td>
              ))}
            </tr>
            <tr>
              <td className="text-slate-500 dark:text-slate-400">Danh mục</td>
              {data.products.map((p) => (
                <td key={p.id} className="font-medium text-slate-800 dark:text-slate-100">
                  {p.categoryName}
                </td>
              ))}
            </tr>
            <tr>
              <td className="text-slate-500 dark:text-slate-400">Tình trạng</td>
              {data.products.map((p) => (
                <td key={p.id}>
                  <span
                    className={`badge ${p.availableQuantity > 0 ? 'badge-emerald' : 'badge-slate'}`}
                  >
                    {p.availableQuantity > 0 ? `Còn ${p.availableQuantity}` : 'Hết hàng'}
                  </span>
                </td>
              ))}
            </tr>

            {data.specKeys.map((key) => (
              <tr key={key}>
                <td className="text-slate-500 dark:text-slate-400">{specLabel(key)}</td>
                {data.specs.map((s, i) => (
                  <td
                    key={`${key}-${i}`}
                    className="font-medium text-slate-800 dark:text-slate-100"
                  >
                    {specValue(s?.[key])}
                  </td>
                ))}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  )
}
