import AuthGuard from '@/components/AuthGuard'
import AuditLogView from '@/views/admin/AuditLogView'

export default function AdminAuditLogPage() {
  return (
    <AuthGuard roles={['ADMIN']}>
      <AuditLogView />
    </AuthGuard>
  )
}
