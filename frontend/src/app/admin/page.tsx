import AuthGuard from '@/components/AuthGuard'
import DashboardView from '@/views/admin/DashboardView'

// Báo cáo doanh thu chỉ dành cho ADMIN — STAFF gọi vào sẽ bị backend trả 403.
export default function AdminDashboardPage() {
  return (
    <AuthGuard roles={['ADMIN']}>
      <DashboardView />
    </AuthGuard>
  )
}
