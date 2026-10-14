'use client'

import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useState,
  type ReactNode,
} from 'react'
import * as cartApi from '@/lib/api/cart'
import { useAuth } from '@/contexts/AuthContext'
import type { CartResponse } from '@/types'

interface CartContextType {
  cart: CartResponse | null
  isLoading: boolean
  /** So mon trong gio - dung cho huy hieu tren thanh dieu huong. */
  count: number
  refresh: () => Promise<void>
  addItem: (variantId: number, quantity: number) => Promise<CartResponse>
  updateItem: (itemId: number, quantity: number) => Promise<CartResponse>
  removeItem: (itemId: number) => Promise<CartResponse>
  clear: () => Promise<void>
}

const CartContext = createContext<CartContextType | null>(null)

/**
 * Gio hang giu o mot cho de huy hieu tren header va trang /cart khong lech nhau.
 * Moi thao tac deu tra ve nguyen ca gio tu backend, nen chi can thay state bang
 * response - khong tu cong tru o client roi doan sai `availableQuantity`.
 */
export function CartProvider({ children }: { children: ReactNode }) {
  const { user } = useAuth()
  const [cart, setCart] = useState<CartResponse | null>(null)
  const [isLoading, setIsLoading] = useState(false)

  const isCustomer = user?.role === 'CUSTOMER'

  const refresh = useCallback(async () => {
    if (!isCustomer) {
      setCart(null)
      return
    }
    setIsLoading(true)
    try {
      setCart(await cartApi.getCart())
    } catch {
      // Gio hang loi khong duoc lam vo trang dang xem; man hinh /cart tu bao loi.
    } finally {
      setIsLoading(false)
    }
  }, [isCustomer])

  useEffect(() => {
    void refresh()
  }, [refresh])

  const addItem = useCallback(async (variantId: number, quantity: number) => {
    const next = await cartApi.addToCart(variantId, quantity)
    setCart(next)
    return next
  }, [])

  const updateItem = useCallback(async (itemId: number, quantity: number) => {
    const next = await cartApi.updateCartItem(itemId, quantity)
    setCart(next)
    return next
  }, [])

  const removeItem = useCallback(async (itemId: number) => {
    const next = await cartApi.removeCartItem(itemId)
    setCart(next)
    return next
  }, [])

  const clear = useCallback(async () => {
    await cartApi.clearCart()
    await refresh()
  }, [refresh])

  return (
    <CartContext.Provider
      value={{
        cart,
        isLoading,
        count: cart?.totalItems ?? 0,
        refresh,
        addItem,
        updateItem,
        removeItem,
        clear,
      }}
    >
      {children}
    </CartContext.Provider>
  )
}

export function useCart(): CartContextType {
  const ctx = useContext(CartContext)
  if (!ctx) throw new Error('useCart must be used inside <CartProvider>')
  return ctx
}
