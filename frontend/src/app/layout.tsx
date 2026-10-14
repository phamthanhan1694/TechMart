import type { Metadata } from 'next'
import './globals.css'
import { Inter } from 'next/font/google'
import { ThemeProvider } from '@/contexts/ThemeContext'
import { AuthProvider } from '@/contexts/AuthContext'
import { CartProvider } from '@/contexts/CartContext'
import { ToastProvider } from '@/contexts/ToastContext'
import { WishlistProvider } from '@/contexts/WishlistContext'

// Inter — chữ số hẹp và đều, hợp với trang dày bảng giá; dấu tiếng Việt đầy đủ.
// Biến font (variable font) nên không phải liệt kê từng độ đậm.
const inter = Inter({
  subsets: ['vietnamese', 'latin'],
  variable: '--font-inter',
  display: 'swap',
  preload: true,
})

export const metadata: Metadata = {
  title: 'TechMart – Điện thoại, laptop chính hãng',
  description:
    'Cửa hàng thiết bị điện tử: giữ hàng có hạn dùng khi thanh toán, mở bán giới hạn theo suất, tra cứu bảo hành theo serial/IMEI.',
}

/**
 * Inject trước khi React hydrate — tránh flash of unstyled content (FOUC).
 * Đọc localStorage, nếu không có thì theo prefers-color-scheme.
 */
const themeInitScript = `(function(){try{
  var t=localStorage.getItem('theme'),
      d=window.matchMedia('(prefers-color-scheme:dark)').matches;
  if(t==='dark'||(!t&&d)) document.documentElement.classList.add('dark');
}catch(e){}})();`

export default function RootLayout({
  children,
}: {
  children: React.ReactNode
}) {
  return (
    <html lang="vi" className={inter.variable} suppressHydrationWarning>
      <head>
        {/* Anti-FOUC: inject theme before first paint */}
        <script dangerouslySetInnerHTML={{ __html: themeInitScript }} />
      </head>
      <body>
        <ThemeProvider>
          <AuthProvider>
            <ToastProvider>
              <CartProvider>
                <WishlistProvider>{children}</WishlistProvider>
              </CartProvider>
            </ToastProvider>
          </AuthProvider>
        </ThemeProvider>
      </body>
    </html>
  )
}
