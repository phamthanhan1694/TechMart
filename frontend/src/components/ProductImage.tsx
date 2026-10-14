'use client'

import Image from 'next/image'
import { useId, useMemo, useState } from 'react'

interface Props {
  src: string | null | undefined
  alt: string
  /** Gợi ý kích thước cho next/image, mặc định hợp với lưới sản phẩm. */
  sizes?: string
  priority?: boolean
  className?: string
}

/** Từ khoá nhận ra laptop trong tên model; còn lại coi là điện thoại. */
const LAPTOP_HINT =
  /macbook|thinkpad|thinkbook|ideapad|legion|loq|xps|inspiron|vostro|latitude|spectre|envy|pavilion|victus|probook|zenbook|vivobook|zephyrus|tuf|proart|nitro|swift|aspire|predator|katana|modern|cyborg|prestige|laptop/i

/** Băm tên thành một số ổn định — cùng tên luôn ra cùng màu, kể cả sau khi tải lại. */
function hashOf(text: string): number {
  let h = 0
  for (let i = 0; i < text.length; i++) {
    h = (h << 5) - h + text.charCodeAt(i)
    h |= 0
  }
  return Math.abs(h)
}

/**
 * Ảnh sản phẩm, có ô giữ chỗ tự vẽ khi chưa có ảnh thật.
 *
 * Catalog demo không đi kèm ảnh: ảnh sản phẩm thật thuộc bản quyền của hãng hoặc
 * của trang bán hàng, nên không thể đưa vào repo. Thay vì để trống hay hiện biểu
 * tượng "ảnh hỏng", component tự vẽ ô giữ chỗ có màu **suy ra từ tên model** —
 * mỗi máy một sắc riêng và không đổi giữa các lần tải, nên trang danh sách vẫn
 * phân biệt được sản phẩm thay vì thành một dãy ô xám giống hệt nhau.
 *
 * Dải màu chọn ở độ sáng trung bình để đọc được trên cả nền sáng lẫn nền tối —
 * bản thân ô đã là nền của chính nó, nên không cần đổi màu theo theme.
 *
 * Khi có ảnh thật, chỉ cần backend trả `thumbnailUrl` là component dùng ngay.
 */
export default function ProductImage({
  src,
  alt,
  sizes = '(max-width: 640px) 45vw, (max-width: 1024px) 30vw, 220px',
  priority,
  className = '',
}: Props) {
  const [broken, setBroken] = useState(false)
  const gradientId = useId()

  const look = useMemo(() => {
    const h = hashOf(alt || 'san-pham')
    // Giới hạn trong dải lam → tím → hồng: không đụng sắc đỏ thương hiệu vốn
    // đã dành riêng cho giá và nút mua.
    const hue = 205 + (h % 110)
    return {
      from: `hsl(${hue} 34% 62%)`,
      to: `hsl(${hue + 20} 32% 48%)`,
      isLaptop: LAPTOP_HINT.test(alt),
    }
  }, [alt])

  if (src && !broken) {
    return (
      <div className={`thumb-frame ${className}`}>
        <Image
          src={src}
          alt={alt}
          fill
          sizes={sizes}
          priority={priority}
          onError={() => setBroken(true)}
          className="object-contain p-2"
        />
      </div>
    )
  }

  return (
    <div className={`thumb-frame ${className}`}>
      <svg
        viewBox="0 0 200 200"
        className="w-full h-full"
        role="img"
        aria-label={alt}
        preserveAspectRatio="xMidYMid slice"
      >
        <defs>
          <linearGradient id={gradientId} x1="0" y1="0" x2="1" y2="1">
            <stop offset="0%" stopColor={look.from} />
            <stop offset="100%" stopColor={look.to} />
          </linearGradient>
        </defs>

        <rect width="200" height="200" fill={`url(#${gradientId})`} />

        {look.isLaptop ? (
          <g fill="none" stroke="#fff" strokeWidth="3" strokeLinejoin="round" opacity="0.72">
            <rect x="52" y="66" width="96" height="62" rx="4" />
            <path d="M38 134h124l8 12H30z" />
          </g>
        ) : (
          <g fill="none" stroke="#fff" strokeWidth="3" strokeLinejoin="round" opacity="0.72">
            <rect x="74" y="48" width="52" height="104" rx="9" />
            <line x1="92" y1="58" x2="108" y2="58" />
            <circle cx="100" cy="142" r="3" />
          </g>
        )}
      </svg>
    </div>
  )
}
