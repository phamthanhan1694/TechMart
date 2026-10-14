import AuthGuard from '@/components/AuthGuard'
import StoreShell from '@/components/StoreShell'
import OrderListView from '@/views/order/OrderListView'

export default function OrdersPage() {
  return (
    <AuthGuard roles={['CUSTOMER']}>
      <StoreShell>
        <OrderListView />
      </StoreShell>
    </AuthGuard>
  )
}
