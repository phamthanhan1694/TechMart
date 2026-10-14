// /api/v1/products, /categories, /brands (catalog/web/*Controller).

import { api, qs } from '@/lib/api/client'
import type {
  BrandResponse,
  CategoryResponse,
  CompareResponse,
  CreateProductRequest,
  PageResponse,
  ProductDetailResponse,
  ProductSearchParams,
  ProductSummaryResponse,
  UpdateProductRequest,
  VariantRequest,
  VariantResponse,
} from '@/types'

/**
 * Tim kiem san pham. Bo loc thong so di duoi dang `spec.<key>=<value>` -
 * backend chi nhan key nam trong whitelist, key la se bi bo qua chu khong loi.
 */
export function searchProducts(
  params: ProductSearchParams = {},
): Promise<PageResponse<ProductSummaryResponse>> {
  const { specFilter, ...rest } = params
  const specParams = Object.fromEntries(
    Object.entries(specFilter ?? {})
      .filter(([, v]) => v)
      .map(([k, v]) => [`spec.${k}`, v]),
  )
  return api.get<PageResponse<ProductSummaryResponse>>(
    `/products${qs({ ...rest, ...specParams })}`,
  )
}

export function getProduct(slug: string): Promise<ProductDetailResponse> {
  return api.get<ProductDetailResponse>(`/products/${encodeURIComponent(slug)}`)
}

/** So sanh 2-3 san pham. Backend tu hop nhat cac key spec xuat hien. */
export function compareProducts(ids: number[]): Promise<CompareResponse> {
  const sp = new URLSearchParams()
  ids.forEach((id) => sp.append('ids', String(id)))
  return api.get<CompareResponse>(`/products/compare?${sp.toString()}`)
}

export function listCategories(): Promise<CategoryResponse[]> {
  return api.get<CategoryResponse[]>('/categories')
}

export function listBrands(): Promise<BrandResponse[]> {
  return api.get<BrandResponse[]>('/brands')
}

// ── ADMIN ─────────────────────────────────────────────────────────────────

export function createProduct(req: CreateProductRequest): Promise<ProductDetailResponse> {
  return api.post<ProductDetailResponse>('/products', req)
}

export function updateProduct(
  id: number,
  req: UpdateProductRequest,
): Promise<ProductDetailResponse> {
  return api.put<ProductDetailResponse>(`/products/${id}`, req)
}

/** Xoa mem: backend set deleted_at vi san pham co the dang nam trong don cu. */
export function deleteProduct(id: number): Promise<void> {
  return api.del<void>(`/products/${id}`)
}

export function addVariant(productId: number, req: VariantRequest): Promise<VariantResponse> {
  return api.post<VariantResponse>(`/products/${productId}/variants`, req)
}

export function updateVariant(variantId: number, req: VariantRequest): Promise<VariantResponse> {
  return api.put<VariantResponse>(`/variants/${variantId}`, req)
}

/** delta am la giam, duong la tang. Ly do bat buoc - moi lan deu vao log. */
export function adjustStock(
  variantId: number,
  delta: number,
  reason: string,
): Promise<VariantResponse> {
  return api.patch<VariantResponse>(`/variants/${variantId}/stock`, { delta, reason })
}
