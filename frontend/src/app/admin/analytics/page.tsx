import AuthGuard from '@/components/AuthGuard'
import AnalyticsView from '@/views/admin/AnalyticsView'

// Thống kê là số liệu kinh doanh - chỉ ADMIN. Backend chặn STAFF bằng 403.
export default function AdminAnalyticsPage() {
  return (
    <AuthGuard roles={['ADMIN']}>
      <AnalyticsView />
    </AuthGuard>
  )
}
