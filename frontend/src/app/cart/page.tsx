import AuthGuard from '@/components/AuthGuard'
import StoreShell from '@/components/StoreShell'
import CartView from '@/views/cart/CartView'

export default function CartPage() {
  return (
    <AuthGuard roles={['CUSTOMER']}>
      <StoreShell>
        <CartView />
      </StoreShell>
    </AuthGuard>
  )
}
