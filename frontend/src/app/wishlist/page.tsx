import AuthGuard from '@/components/AuthGuard'
import StoreShell from '@/components/StoreShell'
import WishlistView from '@/views/account/WishlistView'

export default function WishlistPage() {
  return (
    <AuthGuard roles={['CUSTOMER']}>
      <StoreShell>
        <WishlistView />
      </StoreShell>
    </AuthGuard>
  )
}
