import AuthGuard from '@/components/AuthGuard'
import StoreShell from '@/components/StoreShell'
import AccountView from '@/views/account/AccountView'

// Mọi vai trò đều có hồ sơ và mật khẩu; sổ địa chỉ chỉ hiện cho khách hàng.
export default function AccountPage() {
  return (
    <AuthGuard>
      <StoreShell>
        <AccountView />
      </StoreShell>
    </AuthGuard>
  )
}
