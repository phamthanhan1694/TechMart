import AuthGuard from '@/components/AuthGuard'
import AdminFlashSaleView from '@/views/admin/AdminFlashSaleView'

export default function AdminFlashSalesPage() {
  return (
    <AuthGuard roles={['ADMIN']}>
      <AdminFlashSaleView />
    </AuthGuard>
  )
}
