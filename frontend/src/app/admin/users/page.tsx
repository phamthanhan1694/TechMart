import AuthGuard from '@/components/AuthGuard'
import AdminUsersView from '@/views/admin/AdminUsersView'

export default function AdminUsersPage() {
  return (
    <AuthGuard roles={['ADMIN']}>
      <AdminUsersView />
    </AuthGuard>
  )
}
