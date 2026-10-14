import AuthGuard from '@/components/AuthGuard'
import StoreShell from '@/components/StoreShell'
import OrderDetailView from '@/views/order/OrderDetailView'

export default async function OrderDetailPage({
  params,
}: {
  params: Promise<{ code: string }>
}) {
  const { code } = await params
  return (
    <AuthGuard>
      <StoreShell>
        <OrderDetailView code={code} />
      </StoreShell>
    </AuthGuard>
  )
}
