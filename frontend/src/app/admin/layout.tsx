import AuthGuard from '@/components/AuthGuard'
import AdminShell from '@/components/AdminShell'

// STAFF và ADMIN đều vào được khu quản trị; từng trang bên trong tự siết thêm.
export default function AdminLayout({ children }: { children: React.ReactNode }) {
  return (
    <AuthGuard roles={['ADMIN', 'STAFF']}>
      <AdminShell>{children}</AdminShell>
    </AuthGuard>
  )
}
