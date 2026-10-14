'use client'

import { Moon, Sun } from 'lucide-react'
import { useTheme } from '@/contexts/ThemeContext'

export default function ThemeToggle() {
  const { theme, toggleTheme } = useTheme()

  return (
    <button
      onClick={toggleTheme}
      className="theme-toggle"
      title={theme === 'dark' ? 'Chuyển sang sáng' : 'Chuyển sang tối'}
      aria-label="Đổi giao diện sáng/tối"
      id="btn-theme-toggle"
    >
      {theme === 'dark' ? <Sun size={16} /> : <Moon size={16} />}
    </button>
  )
}
