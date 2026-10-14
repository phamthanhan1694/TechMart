import { Suspense } from 'react'
import StoreShell from '@/components/StoreShell'
import CompareView from '@/views/catalog/CompareView'
import { Loading } from '@/components/Feedback'

export default function ComparePage() {
  return (
    <StoreShell>
      <Suspense fallback={<Loading />}>
        <CompareView />
      </Suspense>
    </StoreShell>
  )
}
