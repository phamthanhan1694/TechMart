import AuthGuard from '@/components/AuthGuard'
import StoreShell from '@/components/StoreShell'
import CheckoutView from '@/views/checkout/CheckoutView'

export default function CheckoutPage() {
  return (
    <AuthGuard roles={['CUSTOMER']}>
      <StoreShell>
        <CheckoutView />
      </StoreShell>
    </AuthGuard>
  )
}
