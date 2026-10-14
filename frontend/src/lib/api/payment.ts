// /api/v1/payments (payment/web/PaymentController).
//
// Luong that: initiate -> cong thanh toan goi webhook -> don chuyen PAID.
// Trong project khong co cong that, nen co san `simulate-callback` tra ve body
// da ky HMAC de dan thang vao webhook - dung de demo tron ven duong di.

import { api, newIdempotencyKey } from '@/lib/api/client'
import type {
  PaymentMethod,
  PaymentResponse,
  PaymentStatus,
  SimulatedCallback,
  WebhookRequest,
} from '@/types'

/** Header `Idempotency-Key` bat buoc. Goi lai cung key tra ve dung giao dich cu. */
export function initiatePayment(
  orderCode: string,
  method: PaymentMethod,
  idempotencyKey: string = newIdempotencyKey(),
): Promise<PaymentResponse> {
  return api.post<PaymentResponse>('/payments', { orderCode, method }, { idempotencyKey })
}

/** [Demo] Lay san body callback da ky, khoi phai tu tinh HMAC. */
export function simulateCallback(
  paymentId: number,
  outcome: PaymentStatus = 'SUCCEEDED',
): Promise<SimulatedCallback> {
  return api.post<SimulatedCallback>(`/payments/${paymentId}/simulate-callback?outcome=${outcome}`)
}

/**
 * Gui callback vao webhook. Endpoint cong khai nhung bat buoc chu ky HMAC hop le,
 * va backend con doi chieu so tien voi tong don truoc khi danh dau da tra.
 */
export function sendWebhook(req: WebhookRequest): Promise<PaymentResponse> {
  return api.post<PaymentResponse>('/payments/webhook', req)
}

export function paymentsOfOrder(orderCode: string): Promise<PaymentResponse[]> {
  return api.get<PaymentResponse[]>(`/payments/by-order/${encodeURIComponent(orderCode)}`)
}

/** Tien ich demo: initiate -> ky -> ban webhook, tra ve giao dich sau cung. */
export async function payNow(
  orderCode: string,
  method: PaymentMethod,
  outcome: PaymentStatus = 'SUCCEEDED',
): Promise<PaymentResponse> {
  const payment = await initiatePayment(orderCode, method)
  const signed = await simulateCallback(payment.id, outcome)
  return sendWebhook({
    transactionRef: signed.transactionRef,
    status: signed.status,
    amount: signed.amount,
    signature: signed.signature,
  })
}
