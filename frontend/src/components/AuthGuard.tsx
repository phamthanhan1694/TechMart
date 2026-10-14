'use client'

import { Suspense, useEffect, useState } from 'react'
import { usePathname, useRouter, useSearchParams } from 'next/navigation'
import { ShieldAlert } from 'lucide-react'
import Link from 'next/link'
import { getToken, getUser } from '@/lib/auth'
import { EmptyState, FullPageLoading } from '@/components/Feedback'
import type { Role } from '@/types'

interface Props {
  children: React.ReactNode
  /** Bỏ trống = chỉ cần đăng nhập. Có giá trị = phải thuộc một trong các vai trò này. */
  roles?: Role[]
}

function AuthGuardInner({ children, roles }: Props) {
  const router = useRouter()
  const pathname = usePathname()
  const searchParams = useSearchParams()
  const [state, setState] = useState<'checking' | 'ready' | 'forbidden'>('checking')

  useEffect(() => {
    if (!getToken()) {
      // Giữ lại trang đang muốn vào để đăng nhập xong quay về đúng chỗ.
      const query = searchParams.toString()
      const next = encodeURIComponent(pathname + (query ? `?${query}` : ''))
      router.replace(`/login?next=${next}`)
      return
    }
    const user = getUser()
    if (roles && roles.length > 0 && (!user || !roles.includes(user.role))) {
      setState('forbidden')
      return
    }
    setState('ready')
  }, [router, pathname, searchParams, roles])

  if (state === 'checking') return <FullPageLoading />

  if (state === 'forbidden') {
    return (
      <div className="min-h-screen flex items-center justify-center bg-[#e8edf4] dark:bg-surface p-4">
        <div className="card p-8 max-w-md w-full">
          <EmptyState
            icon={ShieldAlert}
            title="Không đủ quyền truy cập"
            desc="Tài khoản của bạn không được phép mở trang này. Đăng nhập bằng tài khoản có quyền phù hợp để tiếp tục."
            action={
              <Link href="/" className="btn btn-primary">
                Về trang chủ
              </Link>
            }
          />
        </div>
      </div>
    )
  }

  return <>{children}</>
}

/**
 * Chặn trang cần đăng nhập, kèm giới hạn vai trò tuỳ chọn.
 *
 * Đây chỉ là hàng rào giao diện — quyền thật do backend quyết định
 * (@PreAuthorize + kiểm quyền sở hữu ở tầng service). Giấu nút đi mà backend
 * không chặn thì vẫn là lỗ hổng, nên đừng coi component này là bảo mật.
 *
 * Suspense nằm ở đây vì phần bên trong đọc `useSearchParams`; Next bắt buộc
 * ranh giới đó phải nằm ngoài component đọc tham số.
 */
export default function AuthGuard(props: Props) {
  return (
    <Suspense fallback={<FullPageLoading />}>
      <AuthGuardInner {...props} />
    </Suspense>
  )
}
