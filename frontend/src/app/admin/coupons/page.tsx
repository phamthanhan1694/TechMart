import AuthGuard from '@/components/AuthGuard'
import AdminCouponsView from '@/views/admin/AdminCouponsView'

export default function AdminCouponsPage() {
  return (
    <AuthGuard roles={['ADMIN']}>
      <AdminCouponsView />
    </AuthGuard>
  )
}
