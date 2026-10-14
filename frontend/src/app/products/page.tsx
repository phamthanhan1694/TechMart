import { Suspense } from 'react'
import StoreShell from '@/components/StoreShell'
import ProductListView from '@/views/catalog/ProductListView'
import { Loading } from '@/components/Feedback'

export default function ProductsPage() {
  return (
    <StoreShell>
      <Suspense fallback={<Loading />}>
        <ProductListView />
      </Suspense>
    </StoreShell>
  )
}
