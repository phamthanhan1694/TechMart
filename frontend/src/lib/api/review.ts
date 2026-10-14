// /api/v1/products/{id}/reviews, /api/v1/reviews (review/web/ReviewController).

import { api, qs } from '@/lib/api/client'
import type {
  CreateReviewRequest,
  PageResponse,
  RatingSummary,
  ReviewResponse,
} from '@/types'

export function reviewsOfProduct(
  productId: number,
  page = 0,
  size = 10,
): Promise<PageResponse<ReviewResponse>> {
  return api.get<PageResponse<ReviewResponse>>(
    `/products/${productId}/reviews${qs({ page, size })}`,
  )
}

/** Diem trung binh + phan bo sao, tinh bang GROUP BY o DB. */
export function ratingSummary(productId: number): Promise<RatingSummary> {
  return api.get<RatingSummary>(`/products/${productId}/reviews/summary`)
}

/**
 * Viet danh gia. Chi danh gia duoc dong don hang cua chinh minh va don da DELIVERED;
 * moi dong don chi mot lan (UNIQUE(order_item_id) o DB).
 * `orderItemId` lay tu GET /orders/{code}.
 */
export function createReview(req: CreateReviewRequest): Promise<ReviewResponse> {
  return api.post<ReviewResponse>('/reviews', req)
}

export function updateReview(
  id: number,
  rating: number,
  comment?: string,
): Promise<ReviewResponse> {
  return api.put<ReviewResponse>(`/reviews/${id}`, { rating, comment })
}

export function deleteReview(id: number): Promise<void> {
  return api.del<void>(`/reviews/${id}`)
}
