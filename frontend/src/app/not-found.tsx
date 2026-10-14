import Link from 'next/link'
import StoreShell from '@/components/StoreShell'

export default function NotFound() {
  return (
    <StoreShell>
      <div className="card p-10 text-center max-w-md mx-auto">
        <p className="text-4xl font-extrabold text-primary-600 dark:text-primary-400">404</p>
        <h1 className="text-lg font-bold text-slate-800 dark:text-slate-100 mt-2">
          Không tìm thấy trang
        </h1>
        <p className="text-sm text-slate-500 dark:text-slate-400 mt-1 leading-relaxed">
          Đường dẫn không tồn tại hoặc sản phẩm đã bị gỡ khỏi cửa hàng.
        </p>
        <Link href="/" className="btn btn-primary mt-5">
          Về trang chủ
        </Link>
      </div>
    </StoreShell>
  )
}
