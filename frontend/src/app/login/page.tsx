import { Suspense } from 'react'
import LoginView from '@/views/auth/LoginView'
import { FullPageLoading } from '@/components/Feedback'

export default function LoginPage() {
  return (
    <Suspense fallback={<FullPageLoading />}>
      <LoginView />
    </Suspense>
  )
}
